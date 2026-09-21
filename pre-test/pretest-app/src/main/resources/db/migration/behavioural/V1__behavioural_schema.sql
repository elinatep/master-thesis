-- Behavioural schema for the "feeling heard" pre-test. Kept separate from the portal's operational
-- data so analysed data stays clean of portal content.
--
-- Flyway is configured with this schema and runs the script inside it, so the objects below are
-- unqualified. The portal migrates its own schema with a separate Flyway instance and a separate
-- history table; the two version series are unrelated and both start at V1.
--
-- No transcript table: the pre-test has no voice interaction at all ("No microphone is needed"),
-- so there is nothing spoken to transcribe. It comes back for Experiment 2.

-- One row per participant run of the portal. participant_id is the pseudonymous key handed over by
-- Qualtrics - the same value the portal stores as its opaque account_ref, which links study data to
-- business data without the portal knowing that participants exist.
--
-- arm is which version of the claim outcome they were shown (S0 control, S1 unfair, S2 uncertain,
-- S4 portal failure). A pre-test compares candidate situations rather than crossing fixed factors,
-- so this is a name, not a code: which arms exist, and every word each one shows, lives in
-- configuration/arms/*.json. The handover rejects a name with no arm file, so nothing unrecognised
-- reaches this column.
CREATE TABLE study_session (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    participant_id  VARCHAR(64)  NOT NULL,
    arm             VARCHAR(64)  NOT NULL,
    user_agent      TEXT,
    started_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    ended_at        TIMESTAMPTZ,
    completed       BOOLEAN      NOT NULL DEFAULT false,
    task_success    BOOLEAN
);

CREATE INDEX idx_session_participant ON study_session(participant_id);
CREATE INDEX idx_session_arm ON study_session(arm);

-- Generic behavioural event. Common, analysis-critical fields are real columns; the variable,
-- type-specific payload lives in `data` (JSONB), so a new event type needs no migration.
--
-- Two streams, told apart by `source`:
--  * CLIENT - emitted by the browser. client_seq is its monotonic per-session counter, used to
--    order events despite network reordering.
--  * SERVER - emitted by the portal's own domain services through PortalEventSink. No browser
--    counter, hence the nullable client_seq; these order by server_ts.
--
-- Timestamps matter more than usual here. The pre-test is largely asking whether the scenario
-- lands - whether people read the briefing, whether the claim outcome registers - and time on each
-- step is most of the evidence for that.
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

-- Qualtrics handover. Qualtrics POSTs the participant's condition (participantId, arm, name,
-- callbackUrl) to /api/handover and receives a short-lived opaque token; it then redirects the
-- participant here with ?t=, which is exchanged on entry. Stored in the database rather than in
-- memory so a token survives an app restart between the register call and the redirect. Reusable
-- within its TTL.
CREATE TABLE handover (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    token           VARCHAR(64)  NOT NULL UNIQUE,
    participant_id  VARCHAR(64)  NOT NULL,
    arm             VARCHAR(64)  NOT NULL,
    full_name       VARCHAR(200),
    callback_url    TEXT,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    expires_at      TIMESTAMPTZ  NOT NULL
);
