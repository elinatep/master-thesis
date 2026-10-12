package solutions.andreas.study;

import java.security.Principal;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import solutions.andreas.study.call.CallTokenService;

/**
 * What the human agent's console talks to: the queue, the brief for one handover, picking it up,
 * the token for the call, and ending it.
 *
 * <p>Everything here is behind the agent credential (see {@link StudySecurityConfig}) - the entries
 * carry participant ids and what the assistant was just told, and every participant knows this host
 * because they are redirected to it.
 *
 * <h2>What the agent is deliberately not told</h2>
 *
 * <p><b>The AI condition.</b> Nothing in any payload here says whether the assistant acknowledged
 * the participant's feelings. The agent is told only {@code humanAck}, which is their own
 * instruction. They can read the transcript and will often infer the rest, and blinding is
 * therefore impossible - but inferring it from the transcript is at least the same information
 * every time, whereas printing it on the card would make an expectancy effect uniform across every
 * session rather than something that varies with how closely they read.
 *
 * <p>This is a decision of the study's, not an accident of the API, so
 * {@code AgentConsoleTest} asserts that no response from here contains the AI factor. A later
 * convenience field would otherwise quietly undo it.
 */
@RestController
@RequestMapping("/api/agent")
public class AgentConsoleController {

    private static final Logger log = LoggerFactory.getLogger(AgentConsoleController.class);

    private final AgentHandoverRepository handovers;
    private final StudySessionRepository sessions;
    private final TranscriptEntryRepository transcripts;
    private final CallTokenService callTokens;

    public AgentConsoleController(AgentHandoverRepository handovers, StudySessionRepository sessions,
            TranscriptEntryRepository transcripts, CallTokenService callTokens) {
        this.handovers = handovers;
        this.sessions = sessions;
        this.transcripts = transcripts;
        this.callTokens = callTokens;
    }

    /**
     * The queue: who is still waiting, longest wait first.
     *
     * <p>{@code humanAck} is joined from the session rather than stored on the handover, so it is
     * read-only here by construction - nothing an agent does during a call can change the condition
     * they were assigned.
     */
    @GetMapping("/queue")
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

    /**
     * Claim a waiting handover, and get everything needed to open the call well.
     *
     * <p>First caller wins. A second agent asking for an already-claimed handover gets a 409 rather
     * than a second brief: two people picking up the same participant is the discontinuity this
     * experiment measures, manufactured by a race.
     */
    @PostMapping("/handover/{id}/pickup")
    @Transactional
    public Brief pickUp(@PathVariable Long id, Principal principal) {
        AgentHandover h = handovers.findById(id).orElseThrow(() -> notFound(id));
        if (h.getEndedAt() != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "That call has already ended");
        }
        if (h.getPickedUpAt() != null) {
            String who = h.getAgentId() == null ? "another agent" : h.getAgentId();
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Already picked up by " + who + " at " + h.getPickedUpAt());
        }
        String agentId = principal == null ? "unknown" : principal.getName();
        h.pickUp(agentId);
        handovers.save(h);
        log.info("Handover picked up: handover={} participant={} agent={}",
                h.getId(), h.getParticipantId(), agentId);
        return briefFor(h);
    }

    /**
     * The brief again, without claiming anything. A console that is reloaded mid-call - which will
     * happen - has to be able to get back to the same screen.
     */
    @GetMapping("/handover/{id}")
    public Brief brief(@PathVariable Long id) {
        return briefFor(handovers.findById(id).orElseThrow(() -> notFound(id)));
    }

    /**
     * A token for the agent's side of the call, plus the call to join.
     *
     * <p>Minted per request and short-lived. Separate from the pickup so a dropped connection can
     * be recovered without the handover looking like it was picked up twice.
     */
    @PostMapping("/handover/{id}/call-token")
    public CallCredentials callToken(@PathVariable Long id) {
        AgentHandover h = handovers.findById(id).orElseThrow(() -> notFound(id));
        if (h.getEndedAt() != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "That call has already ended");
        }
        if (!callTokens.isConfigured()) {
            // 503, not 500: the deployment is missing configuration rather than broken, and the
            // console can say which setting without the agent reading a stack trace.
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Calling is not configured on this deployment (COMMUNICATION_CONNECTION_STRING)");
        }
        CallTokenService.Issued issued = callTokens.issue();
        return new CallCredentials(issued.userId(), issued.token(), issued.expiresOn(),
                h.getCallId().toString());
    }

    /**
     * End the call.
     *
     * <p>Idempotent: hanging up and then clicking "end" - or two consoles doing it - must not be an
     * error the agent has to think about while a participant is still on the line.
     */
    @PostMapping("/handover/{id}/end")
    @Transactional
    public Brief end(@PathVariable Long id) {
        AgentHandover h = handovers.findById(id).orElseThrow(() -> notFound(id));
        if (h.getEndedAt() == null) {
            h.end();
            handovers.save(h);
            log.info("Handover ended: handover={} participant={} agent={}",
                    h.getId(), h.getParticipantId(), h.getAgentId());
        }
        return briefFor(h);
    }

    private Brief briefFor(AgentHandover h) {
        StudySession session = sessions.findById(h.getSessionId()).orElse(null);
        List<TranscriptLine> lines = transcripts.findBySessionIdOrderByClientSeqAsc(h.getSessionId())
                .stream()
                .map(t -> new TranscriptLine(t.getRole(), t.getText(), t.getClientTs()))
                .toList();
        return new Brief(
                h.getId(),
                h.getParticipantId(),
                h.getSessionId(),
                // Their own instruction, and only that. See the class comment: the AI factor is not
                // in this payload on purpose.
                session == null ? null : session.getCondition().humanAck(),
                h.getSummary(),
                lines,
                h.getRequestedAt(),
                h.getPickedUpAt(),
                h.getAgentId(),
                h.getEndedAt(),
                h.getCallId().toString(),
                waitedSeconds(h));
    }

    private static ResponseStatusException notFound(Long id) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "No handover " + id);
    }

    private static long waitedSeconds(AgentHandover h) {
        OffsetDateTime until = h.getPickedUpAt() != null ? h.getPickedUpAt() : OffsetDateTime.now();
        return Duration.between(h.getRequestedAt(), until).toSeconds();
    }

    // ---- payloads ------------------------------------------------------------

    public record QueueEntry(Long handoverId, String participantId, Long sessionId, Boolean humanAck,
            Map<String, Object> summary, OffsetDateTime requestedAt, long waitedSeconds) {
    }

    /**
     * Everything the console shows for one handover.
     *
     * <p>{@code humanAck} is the condition card. There is no {@code aiAck} field and there must not
     * be one.
     */
    public record Brief(Long handoverId, String participantId, Long sessionId, Boolean humanAck,
            Map<String, Object> summary, List<TranscriptLine> transcript, OffsetDateTime requestedAt,
            OffsetDateTime pickedUpAt, String agentId, OffsetDateTime endedAt, String callId,
            long waitedSeconds) {
    }

    /** One line of the AI leg, as the agent reads it before picking up. */
    public record TranscriptLine(String role, String text, OffsetDateTime at) {
    }

    /** What the agent's browser needs to join: who it is, the token, and which call. */
    public record CallCredentials(String userId, String token, String expiresOn, String callId) {
    }
}
