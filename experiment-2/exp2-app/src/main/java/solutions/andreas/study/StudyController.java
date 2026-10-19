package solutions.andreas.study;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

/**
 * Behavioural logging intake (behavioural schema). The browser opens a session at the Qualtrics
 * handoff, then streams events and transcript lines as-they-happen (write-as-you-go, per the
 * "logging completeness" constraint), and closes the session on completion/handback.
 *
 * A missing participant id is rejected loudly (400) so a dropped/mis-parsed condition can never
 * silently corrupt group assignment. Every event is tied to a session, hence to a participant.
 */
@RestController
@RequestMapping("/api/study")
public class StudyController {

    private static final Logger log = LoggerFactory.getLogger(StudyController.class);

    private final StudySessionRepository sessions;
    private final EventRepository events;
    private final TranscriptEntryRepository transcripts;
    private final ObjectMapper mapper;

    public StudyController(StudySessionRepository sessions, EventRepository events,
            TranscriptEntryRepository transcripts, ObjectMapper mapper) {
        this.sessions = sessions;
        this.events = events;
        this.transcripts = transcripts;
        this.mapper = mapper;
    }

    /**
     * Open a session for a participant/condition, or hand back the one they already have.
     *
     * <p>Blank participant id => 400 (fail loud).
     *
     * <p><b>Idempotent while a run is in progress.</b> The gate runs on every page load, and a
     * reload is a page load - so this used to start a second session for a participant who
     * refreshed. Everything downstream reads "the participant's most recent session", so that
     * second, empty session became their current one and the first became unreachable: their filed
     * claim no longer counted, the assessment answered "no claim has been filed yet", and an open
     * handover could not be rejoined. A participant who pressed reload mid-study was quietly
     * finished.
     *
     * <p>A session is reused only while it is not completed and the condition matches. A different
     * condition means Qualtrics assigned a new one, which is a genuinely new run and gets its own
     * session rather than silently inheriting the old cell.
     */
    @PostMapping("/session")
    public SessionResponse openSession(@Valid @RequestBody SessionRequest req) {
        String participantId = req.participantId().trim();
        Condition condition = new Condition(req.aiAck(), req.humanAck());

        StudySession existing = sessions
                .findFirstByParticipantIdAndCompletedFalseOrderByStartedAtDesc(participantId)
                .filter(s -> s.getCondition().equals(condition))
                .orElse(null);
        if (existing != null) {
            log.debug("Reusing the open session {} for participant {} ({})",
                    existing.getId(), participantId, condition.cell());
            return new SessionResponse(existing.getId(), existing.getParticipantId(),
                    condition.aiAck(), condition.humanAck());
        }

        StudySession s = sessions.save(new StudySession(participantId, condition, req.userAgent()));
        log.info("Opened session {} for participant {} ({})", s.getId(), participantId, condition.cell());
        return new SessionResponse(s.getId(), s.getParticipantId(), condition.aiAck(), condition.humanAck());
    }

    /** Append a batch of behavioural events; written immediately (not buffered server-side). */
    @PostMapping("/events")
    @Transactional
    public ResponseEntity<Void> logEvents(@Valid @RequestBody EventBatch batch) {
        List<Event> rows = batch.events().stream()
                .map(e -> new Event(batch.sessionId(), e.seq(), e.type(), e.data(), e.clientTs(), Event.CLIENT))
                .toList();
        events.saveAll(rows);
        return ResponseEntity.noContent().build();
    }

    /** Append a batch of voice transcript lines; written immediately as they arrive. */
    @PostMapping("/transcript")
    @Transactional
    public ResponseEntity<Void> logTranscript(@Valid @RequestBody TranscriptBatch batch) {
        List<TranscriptEntry> rows = batch.entries().stream()
                .map(e -> new TranscriptEntry(batch.sessionId(), e.seq(), e.role(), e.text(), e.clientTs()))
                .toList();
        transcripts.saveAll(rows);
        return ResponseEntity.noContent().build();
    }

    /** Close the session out at completion/handback: time-on-task end + task outcome. */
    @PostMapping("/session/{id}/complete")
    @Transactional
    public ResponseEntity<Void> complete(@PathVariable Long id, @RequestBody CompleteRequest req) {
        StudySession s = sessions.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No session " + id));
        s.setCompleted(true);
        s.setEndedAt(OffsetDateTime.now());
        s.setTaskSuccess(req.taskSuccess());
        sessions.save(s);
        return ResponseEntity.noContent().build();
    }

    /**
     * All behavioural data as one flat, ordered stream for the data/export page: every session,
     * event and transcript line as a row, each enriched with its participant and assigned cell.
     * Small N, so we return everything and let the page paginate/search/sort/export client-side.
     */
    @GetMapping("/log")
    public List<LogRow> log() {
        Map<Long, StudySession> byId = new HashMap<>();
        for (StudySession s : sessions.findAll()) {
            byId.put(s.getId(), s);
        }

        List<LogRow> rows = new ArrayList<>();
        for (StudySession s : byId.values()) {
            String detail = "completed=" + s.isCompleted()
                    + (s.getTaskSuccess() != null ? "; taskSuccess=" + s.getTaskSuccess() : "")
                    + (s.getEndedAt() != null ? "; endedAt=" + s.getEndedAt() : "");
            Condition c = s.getCondition();
            rows.add(new LogRow("SESSION", s.getId(), s.getParticipantId(), c.aiAck(), c.humanAck(),
                    null, "SESSION", detail, null, s.getStartedAt(), s.getEndedAt()));
        }
        for (Event e : events.findAll()) {
            StudySession s = byId.get(e.getSessionId());
            rows.add(new LogRow("EVENT", e.getSessionId(), pid(s), aiAck(s), humanAck(s),
                    e.getClientSeq(), e.getType(), toJson(e.getData()), e.getSource(),
                    e.getClientTs(), e.getServerTs()));
        }
        for (TranscriptEntry t : transcripts.findAll()) {
            StudySession s = byId.get(t.getSessionId());
            rows.add(new LogRow("TRANSCRIPT", t.getSessionId(), pid(s), aiAck(s), humanAck(s),
                    t.getClientSeq(), t.getRole(), t.getText(), null, t.getClientTs(), t.getServerTs()));
        }

        // Default order: chronological by when it happened (client time, falling back to server).
        rows.sort(Comparator.comparing(StudyController::orderTs, Comparator.nullsLast(Comparator.naturalOrder())));
        return rows;
    }

    private static String pid(StudySession s) {
        return s == null ? null : s.getParticipantId();
    }

    private static Boolean aiAck(StudySession s) {
        return s == null ? null : s.getCondition().aiAck();
    }

    private static Boolean humanAck(StudySession s) {
        return s == null ? null : s.getCondition().humanAck();
    }

    private static OffsetDateTime orderTs(LogRow r) {
        return r.clientTs() != null ? r.clientTs() : r.serverTs();
    }

    private String toJson(Map<String, Object> data) {
        if (data == null || data.isEmpty()) {
            return null;
        }
        try {
            return mapper.writeValueAsString(data);
        } catch (Exception e) {
            return String.valueOf(data);
        }
    }

    /** One flat row of behavioural data (a session, event, or transcript line). */
    /**
     * One row of the flat export. {@code source} is CLIENT/SERVER for events (see {@link Event}) and
     * null for session and transcript rows, which only ever come from the browser.
     */
    public record LogRow(String kind, Long sessionId, String participantId, Boolean aiAck,
            Boolean humanAck, Long seq, String type, String detail, String source,
            OffsetDateTime clientTs, OffsetDateTime serverTs) {
    }

    // ---- request/response payloads -------------------------------------------

    public record SessionRequest(
            @NotBlank String participantId,
            @NotNull Boolean aiAck,
            @NotNull Boolean humanAck,
            String userAgent) {
    }

    public record SessionResponse(Long sessionId, String participantId, boolean aiAck, boolean humanAck) {
    }

    public record EventBatch(@NotNull Long sessionId, @NotNull List<EventInput> events) {
    }

    public record EventInput(long seq, @NotBlank String type, Map<String, Object> data, OffsetDateTime clientTs) {
    }

    public record TranscriptBatch(@NotNull Long sessionId, @NotNull List<TranscriptInput> entries) {
    }

    public record TranscriptInput(long seq, @NotBlank String role, @NotBlank String text, OffsetDateTime clientTs) {
    }

    public record CompleteRequest(Boolean taskSuccess) {
    }
}
