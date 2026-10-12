package solutions.andreas.study;

import jakarta.validation.Valid;
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
     * Where the participant's transfer has got to. Polled by their own page while they wait.
     *
     * <p>They have just been told a colleague is coming and the assistant has gone quiet, so the
     * page has to be able to say something true in the meantime. Silence after a promise reads as
     * the service having dropped them - which, in a study measuring whether people feel heard, is
     * a confound introduced by the implementation.
     */
    @GetMapping("/study/agent-handover")
    public ParticipantStatus status() {
        String participantId = participants.currentRef();
        StudySession session = sessions.findFirstByParticipantIdOrderByStartedAtDesc(participantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "No open study session for participant " + participantId));
        AgentHandover h = handovers
                .findFirstBySessionIdAndEndedAtIsNullOrderByRequestedAtDesc(session.getId())
                .orElse(null);
        if (h == null) {
            return new ParticipantStatus(null, "none", 0, null);
        }
        QueueTicket ticket = ticketFor(h);
        String state = h.getPickedUpAt() != null ? "connected" : ticket.status();
        // The call id goes out as soon as the transfer exists, not when an agent picks up: the
        // participant starts joining immediately and waits inside the call, so the moment the agent
        // arrives there is nothing left to negotiate.
        return new ParticipantStatus(h.getId(), state, ticket.position(), h.getCallId().toString());
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

    /**
     * What the waiting participant's page is told. No condition in here, in either factor: the page
     * renders the same whatever cell they are in, and the only thing that differs between
     * participants is what they hear.
     */
    public record ParticipantStatus(Long handoverId, String state, int position, String callId) {
    }
}
