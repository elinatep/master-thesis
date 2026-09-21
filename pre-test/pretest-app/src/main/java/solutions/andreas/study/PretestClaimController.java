package solutions.andreas.study;

import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import solutions.andreas.portal.core.spi.AccountRefProvider;

/**
 * What the participant should be shown now, given their arm and how many claim submissions they
 * have made. The study's screens ask this rather than deciding for themselves.
 *
 * <p>Named for the study rather than the domain: the portal library has its own
 * {@code ClaimController} for real claims, and two beans of that name cannot coexist. The name
 * also says the right thing - this one scripts a pre-test, it does not manage claims.
 *
 * <p>There is no submit endpoint here. The participant files their claim through the portal's own
 * form, against the portal's own endpoint; {@link ClaimAttemptFilter} sits in front of that and
 * decides whether this arm lets it succeed. This controller only reports the resulting state, so
 * the answer is the same whether the browser asks on first arrival or after a reload.
 */
@RestController
@RequestMapping("/api/pretest")
public class PretestClaimController {

    private final StudySessionRepository sessions;
    private final EventRepository events;
    private final ArmCatalogue arms;
    private final AccountRefProvider participants;

    public PretestClaimController(StudySessionRepository sessions, EventRepository events,
            ArmCatalogue arms, AccountRefProvider participants) {
        this.sessions = sessions;
        this.events = events;
        this.arms = arms;
        this.participants = participants;
    }

    /**
     * The scripted step for the submission the participant has just made.
     *
     * <p>Derived from the logged attempts, so it survives a reload: a participant who refreshes the
     * outcome screen sees the same outcome, and one who refreshes mid-retry in S4 is still on
     * attempt 2 rather than back at the start.
     */
    @GetMapping("/outcome")
    public Outcome outcome() {
        StudySession session = currentSession();
        Arm arm = session.getArm();
        int attempts = events.countBySessionIdAndType(session.getId(), ClaimAttemptFilter.CLAIM_ATTEMPT);
        if (attempts == 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "No claim has been submitted yet for participant " + session.getParticipantId());
        }
        int total = arms.attemptCount(arm);
        return new Outcome(arm.id(), attempts, total, attempts >= total, arms.attempt(arm, attempts));
    }

    private StudySession currentSession() {
        String participantId = participants.currentRef();
        return sessions.findFirstByParticipantIdOrderByStartedAtDesc(participantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "No open study session for participant " + participantId));
    }

    /**
     * {@code scripted} is the arm file's entry for this attempt, passed through untouched - banner,
     * detail, retry instructions, assessment panel. The backend does not reshape it, so changing
     * what a participant sees is a config edit and a restart.
     */
    public record Outcome(String arm, int attemptNumber, int totalAttempts, boolean finalAttempt,
            Map<String, Object> scripted) {
    }
}
