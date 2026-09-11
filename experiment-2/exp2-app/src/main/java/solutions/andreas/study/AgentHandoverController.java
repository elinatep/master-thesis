package solutions.andreas.study;

import jakarta.validation.Valid;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import solutions.andreas.portal.core.spi.AccountRefProvider;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * The AI-to-human handover: the participant end (the assistant asks for a human) and the agent end
 * (the console reads the queue).
 *
 * <p>This is the piece Experiment 2 adds that levels-of-ai-help has no equivalent of. There, the
 * voice bot is the whole interaction; here it is the first leg of a call that a real person
 * finishes, and the two legs have to look like one conversation to the participant.
 */
@RestController
@RequestMapping("/api")
public class AgentHandoverController {

    private static final Logger log = LoggerFactory.getLogger(AgentHandoverController.class);

    private final AgentHandoverRepository handovers;
    private final StudySessionRepository sessions;
    private final AccountRefProvider participants;
    private final ObjectMapper mapper;

    public AgentHandoverController(AgentHandoverRepository handovers, StudySessionRepository sessions,
            AccountRefProvider participants, ObjectMapper mapper) {
        this.handovers = handovers;
        this.sessions = sessions;
        this.participants = participants;
        this.mapper = mapper;
    }

    /**
     * Called by the browser when the assistant invokes {@code transfer_to_human_agent}. Puts the
     * participant in the queue and answers with their place in it, so the assistant can tell them
     * what is happening before it goes quiet.
     *
     * <p>Idempotent per session. The Realtime data channel can drop and reconnect, and a model can
     * retry a tool call; neither should enqueue the same participant twice, because a duplicate
     * would be answered by a second agent with no memory of the first call - which is precisely
     * the discontinuity this experiment is measuring.
     */
    @PostMapping("/study/agent-handover")
    @Transactional
    public QueueTicket requestAgent(@Valid @RequestBody HandoverRequest req) {
        String participantId = participants.currentRef();
        StudySession session = sessions.findFirstByParticipantIdOrderByStartedAtDesc(participantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "No open study session for participant " + participantId));

        AgentHandover existing = handovers
                .findFirstBySessionIdAndEndedAtIsNullOrderByRequestedAtDesc(session.getId())
                .orElse(null);
        if (existing != null) {
            log.debug("Agent handover already open for participant={} - returning existing ticket", participantId);
            return ticketFor(existing);
        }

        AgentHandover handover = handovers.save(
                new AgentHandover(session.getId(), participantId, parseSummary(req.summary())));
        log.info("Agent handover requested: participant={} cell={} handover={}",
                participantId, session.getCondition().cell(), handover.getId());
        return ticketFor(handover);
    }

    /**
     * The queue an agent console polls: who is waiting, longest first, with the summary the
     * assistant left and the cell the agent has to play.
     *
     * <p>The cell is joined from the session rather than stored on the handover, so it is read-only
     * here by construction. NOT YET AUTHENTICATED - see the note in the README; this endpoint
     * exposes live participant transcript context and must be behind auth before any real run.
     */
    @GetMapping("/agent/queue")
    public List<QueueEntry> queue() {
        return handovers.findByPickedUpAtIsNullOrderByRequestedAtAsc().stream()
                .map(h -> {
                    StudySession s = sessions.findById(h.getSessionId()).orElse(null);
                    return new QueueEntry(
                            h.getId(),
                            h.getParticipantId(),
                            h.getSessionId(),
                            s == null ? null : s.getCondition().humanAck(),
                            h.getSummary(),
                            h.getRequestedAt(),
                            waitedSeconds(h));
                })
                .toList();
    }

    private QueueTicket ticketFor(AgentHandover handover) {
        List<AgentHandover> waiting = handovers.findByPickedUpAtIsNullOrderByRequestedAtAsc();
        for (int i = 0; i < waiting.size(); i++) {
            if (waiting.get(i).getId().equals(handover.getId())) {
                return new QueueTicket(handover.getId(), "waiting", i + 1);
            }
        }
        // Already picked up between the save and this read - rare, but a real race with a fast
        // agent. Reporting "connecting" is truthful and keeps the assistant's line to the
        // participant right ("putting you through" rather than "you are Nth in the queue").
        return new QueueTicket(handover.getId(), "connecting", 0);
    }

    private static long waitedSeconds(AgentHandover h) {
        return Duration.between(h.getRequestedAt(), OffsetDateTime.now()).toSeconds();
    }

    /**
     * The tool call's arguments arrive as the JSON string the model produced. Parse it if we can,
     * but never fail the transfer over it: a malformed summary costs the agent some context, while
     * a rejected transfer strands a participant who has been told a human is coming. The raw string
     * is kept either way, and the same payload is already in the event log verbatim.
     */
    private Map<String, Object> parseSummary(String raw) {
        if (raw == null || raw.isBlank()) {
            return Map.of();
        }
        try {
            return mapper.readValue(raw, new tools.jackson.core.type.TypeReference<Map<String, Object>>() {});
        } catch (JacksonException e) {
            log.warn("Handover summary was not valid JSON; keeping it as raw text", e);
            return Map.of("raw", raw);
        }
    }

    // ---- payloads ------------------------------------------------------------

    /** {@code summary} is the assistant's tool-call arguments, as the JSON string it emitted. */
    public record HandoverRequest(String summary) {
    }

    public record QueueTicket(Long handoverId, String status, int position) {
    }

    public record QueueEntry(Long handoverId, String participantId, Long sessionId, Boolean humanAck,
            Map<String, Object> summary, OffsetDateTime requestedAt, long waitedSeconds) {
    }
}
