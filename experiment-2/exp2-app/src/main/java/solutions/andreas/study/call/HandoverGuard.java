package solutions.andreas.study.call;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import solutions.andreas.portal.core.spi.AccountRefProvider;
import solutions.andreas.study.AgentHandover;
import solutions.andreas.study.AgentHandoverRepository;
import solutions.andreas.study.StudySession;
import solutions.andreas.study.StudySessionRepository;

/**
 * The gate in front of everything a participant can do with a live call.
 *
 * <p>These endpoints are participant-facing on a public host, so the only thing standing between
 * them and anyone who finds the address is this: there has to be a real session, with a real
 * handover that is still open. Without it, minting credentials - for the call or for transcription -
 * is an open invitation to spend someone else's Azure budget.
 *
 * <p>Shared rather than copied because it IS the check. Two copies of a security gate drift, and
 * the one that drifts is the one nobody is looking at.
 */
@Component
public class HandoverGuard {

    private final AgentHandoverRepository handovers;
    private final StudySessionRepository sessions;
    private final AccountRefProvider participants;

    public HandoverGuard(AgentHandoverRepository handovers, StudySessionRepository sessions,
            AccountRefProvider participants) {
        this.handovers = handovers;
        this.sessions = sessions;
        this.participants = participants;
    }

    /** The caller's open handover, or the failure that explains which half is missing. */
    public AgentHandover requireOpenHandover() {
        String participantId = participants.currentRef();
        StudySession session = sessions.findFirstByParticipantIdOrderByStartedAtDesc(participantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "No open study session for participant " + participantId));
        return handovers
                .findFirstBySessionIdAndEndedAtIsNullOrderByRequestedAtDesc(session.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT,
                        "No open handover - there is no call to join yet"));
    }
}
