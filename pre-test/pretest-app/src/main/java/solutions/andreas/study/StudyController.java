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
 * handoff, then streams events as-they-happen (write-as-you-go, per the "logging completeness"
 * constraint), and closes the session on completion/handback.
 *
 * A missing participant id is rejected loudly (400) so a dropped/mis-parsed condition can never
 * silently corrupt group assignment. Every event is tied to a session, hence to a participant.
 */
@RestController
@RequestMapping("/api/study")
public class StudyController {

    private final StudySessionRepository sessions;
    private final EventRepository events;
    private final ObjectMapper mapper;

    public StudyController(StudySessionRepository sessions, EventRepository events,
            ObjectMapper mapper) {
        this.sessions = sessions;
        this.events = events;
        this.mapper = mapper;
    }

    /** Open a session for a participant/condition. Blank participant id => 400 (fail loud). */
    @PostMapping("/session")
    public SessionResponse openSession(@Valid @RequestBody SessionRequest req) {
        Arm arm = new Arm(req.arm());
        StudySession s = sessions.save(new StudySession(req.participantId().trim(), arm, req.userAgent()));
        return new SessionResponse(s.getId(), s.getParticipantId(), arm.id());
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
     * All behavioural data as one flat, ordered stream for the data/export page: every session
     * and event as a row, each enriched with its participant and assigned arm. Small N, so we
     * return everything and let the page paginate/search/sort/export client-side.
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
            rows.add(new LogRow("SESSION", s.getId(), s.getParticipantId(), s.getArm().id(),
                    null, "SESSION", detail, null, s.getStartedAt(), s.getEndedAt()));
        }
        for (Event e : events.findAll()) {
            StudySession s = byId.get(e.getSessionId());
            rows.add(new LogRow("EVENT", e.getSessionId(), pid(s), arm(s),
                    e.getClientSeq(), e.getType(), toJson(e.getData()), e.getSource(),
                    e.getClientTs(), e.getServerTs()));
        }
        // Default order: chronological by when it happened (client time, falling back to server).
        rows.sort(Comparator.comparing(StudyController::orderTs, Comparator.nullsLast(Comparator.naturalOrder())));
        return rows;
    }

    private static String pid(StudySession s) {
        return s == null ? null : s.getParticipantId();
    }

    private static String arm(StudySession s) {
        return s == null ? null : s.getArm().id();
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

    /**
     * One row of the flat export. {@code source} is CLIENT/SERVER for events (see {@link Event})
     * and null for session rows.
     */
    public record LogRow(String kind, Long sessionId, String participantId, String arm,
            Long seq, String type, String detail, String source, OffsetDateTime clientTs,
            OffsetDateTime serverTs) {
    }

    // ---- request/response payloads -------------------------------------------

    public record SessionRequest(
            @NotBlank String participantId,
            @NotBlank String arm,
            String userAgent) {
    }

    public record SessionResponse(Long sessionId, String participantId, String arm) {
    }

    public record EventBatch(@NotNull Long sessionId, @NotNull List<EventInput> events) {
    }

    public record EventInput(long seq, @NotBlank String type, Map<String, Object> data, OffsetDateTime clientTs) {
    }

    public record CompleteRequest(Boolean taskSuccess) {
    }
}
