package solutions.andreas.study.call;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import solutions.andreas.portal.core.spi.AccountRefProvider;
import solutions.andreas.study.AgentHandover;
import solutions.andreas.study.AgentHandoverRepository;
import solutions.andreas.study.StudySession;
import solutions.andreas.study.StudySessionRepository;

/**
 * The participant's side of the call with the human agent.
 *
 * <p>Open, not behind a credential, like the rest of the participant-facing API: they arrive
 * anonymously from Prolific and cannot be given an account. What stands in for authorisation is
 * that a token is only issued to someone who has an open handover of their own - the participant
 * header identifies them, and the call they are given is the one their own transfer created. They
 * cannot ask for somebody else's.
 *
 * <p>The audio does not pass through this application. Both sides get a short-lived token, join the
 * group call named by their handover, and Azure Communication Services carries the media. Nothing
 * is recorded: the ethics application forbids storing voice data, and the server not being in the
 * media path is the simplest way to be sure of it.
 */
@RestController
@RequestMapping("/api/call")
public class CallController {

    private static final Logger log = LoggerFactory.getLogger(CallController.class);

    private final AgentHandoverRepository handovers;
    private final StudySessionRepository sessions;
    private final AccountRefProvider participants;
    private final CallTokenService callTokens;

    public CallController(AgentHandoverRepository handovers, StudySessionRepository sessions,
            AccountRefProvider participants, CallTokenService callTokens) {
        this.handovers = handovers;
        this.sessions = sessions;
        this.participants = participants;
        this.callTokens = callTokens;
    }

    /**
     * A token for this participant's own call, and the call to join.
     *
     * <p>Requires an open handover: there is no call before the assistant transfers, and issuing a
     * token without one would let anyone who found this host mint ACS credentials.
     */
    @PostMapping("/token")
    public Credentials token() {
        String participantId = participants.currentRef();
        StudySession session = sessions.findFirstByParticipantIdOrderByStartedAtDesc(participantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "No open study session for participant " + participantId));
        AgentHandover handover = handovers
                .findFirstBySessionIdAndEndedAtIsNullOrderByRequestedAtDesc(session.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT,
                        "No open handover - there is no call to join yet"));

        if (!callTokens.isConfigured()) {
            // 503 rather than 500: configuration is missing, the application is not broken. The
            // page can then tell the participant the line is unavailable instead of showing them a
            // credential error from inside the Calling SDK.
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Calling is not configured on this deployment");
        }

        CallTokenService.Issued issued = callTokens.issue();
        log.debug("Issued a call token: participant={} handover={}", participantId, handover.getId());
        return new Credentials(issued.userId(), issued.token(), issued.expiresOn(),
                handover.getCallId().toString());
    }

    /**
     * No condition in here, in either factor. The participant's page renders identically in all
     * four cells - the only thing that differs between them is what they hear.
     */
    public record Credentials(String userId, String token, String expiresOn, String callId) {
    }
}
