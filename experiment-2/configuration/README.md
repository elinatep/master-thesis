# configuration/

Everything a participant is shown or told that is not portal content: the voice bot's session and
per-cell instructions, and the task text. All of it is read from the filesystem per request, never
bundled, so wording can be corrected without a rebuild - and so it is all in one place when the
study is written up.

- Native dev: the backend runs from `exp2-app/`, so this resolves via `../configuration`.
- Docker/Azure: the image copies this dir to `/app/configuration` (`VOICE_CONFIG_DIR`).
- The code reads the property `app.config-dir`, which takes that env var. The env var keeps its
  voice-era name; the property does not, because this dir is no longer only voice.

## voice/

OpenAI Realtime **session** configuration, applied when the backend mints an ephemeral
token (`POST /api/voice/token?aiAck=true|false`).

All voice configuration is kept here (not hardcoded in the app) so it is explicit and
traceable: the OpenAI session and the browser-side capture settings both live in this dir.

- `session.json` - the base Realtime session object sent to OpenAI for every voice
  cell (model, voice, audio in/out, transcription, and **turn detection / VAD** - set
  explicitly so turn-taking and latency are constant across sessions). The OpenAI **API
  key** is *not* here - it stays in the `OPENAI_API_KEY` env var and never leaves the server.
- `client.json` - the **browser-side** voice settings, served via `GET /api/voice/client-config`
  and applied by the React app: the `getUserMedia` mic constraints (echo cancellation etc.,
  which affect VAD) and the Realtime SDP URL the browser connects to. The frontend falls back
  to built-in defaults if this can't be fetched.
- `instructions/ai-base.md` - everything the two cells share: language, accent, the claim in
  front of the assistant, how to run the dispute call, and when to transfer.
- `instructions/ai-ack.md` and `instructions/ai-noack.md` - **the manipulation, and nothing
  else**. Each is only the "how to respond to how the customer is feeling" block.
- `tools/ai.json` - the function definitions advertised to the model. One file, both cells.

The backend **composes** the prompt: `ai-base.md`, then the block for the assigned cell, overlaid
onto `session.json` as `instructions`. It does not pick one of two whole prompts.

### Why composed rather than one file per cell

levels-of-ai-help has one prompt per level, and rightly so - each level describes a genuinely
different assistant. Here the two prompts would be near-identical twins differing in one
paragraph, and keeping two long duplicates in step by hand is a losing game. The first typo fixed
in one file and not the other is a silent confound: it changes what one group of participants
hears, it survives review because both files still *look* right, and no test catches it. Composing
makes cross-condition parity structural instead of a matter of discipline.

`VoiceSessionCompositionTest` enforces it against these real files: each cell gets its own block,
neither leaks the other's, and everything before the acknowledgement marker is byte-identical.
If you edit `ai-base.md`, both cells move together. If you add a capability to one variant file,
the test fails.

**One thing to watch in pre-testing.** The two blocks are not the same length - telling an
assistant how to acknowledge takes more words than telling it not to. Prompt length is not
nothing, so the acknowledgement manipulation check (`MC.AI.Acknowledge`) is what has to confirm
the manipulation landed, and it is worth listening to pilot recordings from both cells for any
difference in how *long* or how *fluent* the assistant is, not just whether it acknowledges.

### One tool set for both cells

The assistant can do exactly the same things in both cells. In particular it hands over the full
summary in both: passing context to the colleague is **continuity**, which the design holds
constant, not **acknowledgement**, which it manipulates. Confusing the two would make the factors
impossible to separate. A per-cell tools file would make a capability difference easy to introduce
by accident, and a capability confound is indistinguishable from the effect being measured once
the data is in.

## `study/task.md` - the participant's task

The task card shown beside the portal for the whole session, in every cell (see
`StudySplit.tsx`). Served by `GET /api/study/task` and read per request, so correcting the wording
is a file edit and a restart - no rebuild, no redeploy.

Format is a small Markdown subset, parsed in `study/task.ts`: one leading `# ` heading becomes the
card title, blank-line-separated blocks become paragraphs, and single newlines inside a block are
soft-wrapped away so the file can keep a sane line length. No other Markdown is interpreted - no
lists, no emphasis, no links.

Three things to keep right, all of them study-validity rather than code:

- **It must match the Qualtrics wording exactly.** The card is a reminder of an instruction given
  elsewhere; if it paraphrases, participants are working to two subtly different briefs.
- **Keep it to three or four sentences.** It is on screen for the entire session, and long text
  stops being read.
- **Say nothing about how the agents will behave.** The card is the same text in all four cells.
  Any hint that the assistant or the employee might acknowledge how the participant feels would
  prime the outcome measures and leak the manipulation into the instructions.

## The session is always English, in two places

The study runs in English, so the bot must never answer in anything else - a switched language is a
condition the design does not have, and it lands in the behavioural log as if it were data.

Two separate mechanisms, because there are two separate ways it drifts:

1. **`session.json` -> `audio.input.transcription.language: "en"`.** Left unset, `whisper-1` detects
   the language per utterance, and on short, quiet or noisy audio it misreads - silence in
   particular is transcribed as stray phrases in another language, Portuguese most often. That
   bogus transcript reaches the model as the participant's own turn, so the model mirrors it and
   switches. Pinning the transcriber stops it at the source, and keeps the misreading out of
   `logTranscript` too.
2. **A LANGUAGE block at the top of `instructions/ai-base.md`.** Covers the case where a bad
   transcript gets through anyway, and the participant who simply speaks another language. It also
   draws the line the tools make easy to blur: the correspondence language on communication
   preferences (EN/DE/FR/IT) is portal *data* - setting it to French does not make the bot speak
   French.

Keep both. Either alone leaves a way in: the prompt cannot help if the model never sees English,
and the transcriber hint does nothing about a participant who addresses it in German.

Note `session.json` is posted to OpenAI verbatim (plus `instructions`/`tools`), so every key in it
has to be one the API accepts - there is no room for comment keys.
