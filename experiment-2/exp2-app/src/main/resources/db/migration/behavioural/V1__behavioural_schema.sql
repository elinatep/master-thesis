-- Behavioural schema: the study's logs, kept separate from the portal's operational data so
-- analysed data stays clean of portal content. Adapted from the levels-of-ai-help schema; the
-- database is rebuilt from scratch for each run of the study, so the 2x2 condition replaces the
-- integration level in place rather than arriving as a later migration.
--
-- Flyway is configured with this schema and runs the script inside it, so the objects below are
-- unqualified. Note the schema is deliberately NOT the portal's: the portal migrates its own with a
-- separate Flyway instance and a separate history table, and the two version series are unrelated.

-- One row per participant run of the portal. participant_id is the pseudonymous key handed over by
-- Qualtrics - the same value the portal stores as its opaque account_ref, which is what links study
-- data to business data without the portal knowing that participants exist. Completion and
-- time-on-task close the session out.
--
-- The condition is Experiment 2's 2x2, as TWO columns rather than one packed code: the analysis is
-- a 2x2 ANOVA, so the factors have to come out of the export as separate variables. Packing them
-- into a single 0-3 level (as levels-of-ai-help does, where the IV really is ordinal) would mean
-- decoding them again before every model, and would let a main effect be read off the wrong axis.
--
--   ai_ack     - did the AI voice assistant acknowledge the participant's negative affect?
--   human_ack  - did the human employee acknowledge it after the handover?
CREATE TABLE study_session (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    participant_id  VARCHAR(64)  NOT NULL,
    ai_ack          BOOLEAN      NOT NULL,
    human_ack       BOOLEAN      NOT NULL,
    user_agent      TEXT,
    started_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    ended_at        TIMESTAMPTZ,
    completed       BOOLEAN      NOT NULL DEFAULT false,
    task_success    BOOLEAN
);

CREATE INDEX idx_session_participant ON study_session(participant_id);

-- Generic behavioural event. Common, analysis-critical fields are real columns; the variable,
-- type-specific payload lives in `data` (JSONB), so a new event type needs no migration.
--
-- Two streams, told apart by `source`:
--  * CLIENT - emitted by the browser. client_seq is its monotonic per-session counter, used to
--    order events despite network reordering.
--  * SERVER - emitted by the portal's own domain services through PortalEventSink. No browser
--    counter, hence the nullable client_seq; these order by server_ts. This is the parity-clean
--    stream: it records a domain action identically in all four cells of the design.
CREATE TABLE event (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    session_id  BIGINT       NOT NULL REFERENCES study_session(id) ON DELETE CASCADE,
    client_seq  BIGINT,
    source      VARCHAR(8)   NOT NULL DEFAULT 'CLIENT',
    type        VARCHAR(48)  NOT NULL,
    data        JSONB,
    client_ts   TIMESTAMPTZ,
    server_ts   TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_event_session ON event(session_id);
CREATE INDEX idx_event_source ON event(source);

-- Voice transcript lines. Their own table because this is voice-derived PERSONAL data: the audio
-- itself is never stored - neither the participant's nor, on the second leg, the human agent's -
-- only the recognised text, linked to the pseudonymous participant through the session. Written as
-- it happens, not dumped at the end.
--
-- role is USER, ASSISTANT or HUMAN_AGENT. Both legs of the call land in the SAME session, so the
-- handover boundary is visible in one ordered transcript - which is what makes the handover
-- continuity items (Cross.agent.cont) analysable at all. Left as a plain VARCHAR rather than an
-- enum or a CHECK, as in the source schema, so a new speaker role costs no migration.
CREATE TABLE transcript_entry (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    session_id  BIGINT       NOT NULL REFERENCES study_session(id) ON DELETE CASCADE,
    client_seq  BIGINT       NOT NULL,
    role        VARCHAR(16)  NOT NULL,
    text        TEXT         NOT NULL,
    client_ts   TIMESTAMPTZ,
    server_ts   TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_transcript_session ON transcript_entry(session_id);

-- Qualtrics handover. Qualtrics POSTs the participant's condition (participantId, aiAck, humanAck,
-- name, callbackUrl) to /api/handover and receives a short-lived opaque token; it then redirects
-- the participant to the portal with ?t=, which the portal exchanges on entry to recover the
-- condition. Stored in the database rather than in memory so a token survives an app restart
-- between the register call and the redirect. Reusable within its TTL.
CREATE TABLE handover (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    token           VARCHAR(64)  NOT NULL UNIQUE,
    participant_id  VARCHAR(64)  NOT NULL,
    ai_ack          BOOLEAN      NOT NULL,
    human_ack       BOOLEAN      NOT NULL,
    full_name       VARCHAR(200),
    callback_url    TEXT,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    expires_at      TIMESTAMPTZ  NOT NULL
);
