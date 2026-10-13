package solutions.andreas.study;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import solutions.andreas.portal.core.spi.AccountRefProvider;

/**
 * What the participant is shown once they have filed their claim.
 *
 * <p>The participant files through the portal's own form, against the portal's own endpoint. The
 * study does not intercept that - the claim really is created - it only takes over afterwards, to
 * deliver an assessment the portal has no opinion about.
 *
 * <p>There is no submit endpoint here and no per-arm branching: every participant gets the same
 * outcome. The pre-test's job was to find an outcome script that reliably produces anger without
 * reading as a bug, and this study holds the winner constant - what varies here happens later, on
 * the call.
 */
@RestController
@RequestMapping("/api/study")
public class OutcomeController {

    /** The portal's own event for a claim that was really created. */
    static final String CLAIM_SUBMITTED = "CLAIM_SUBMITTED";

    private final StudySessionRepository sessions;
    private final EventRepository events;
    private final ClaimOutcome outcome;
    private final AccountRefProvider participants;

    public OutcomeController(StudySessionRepository sessions, EventRepository events,
            ClaimOutcome outcome, AccountRefProvider participants) {
        this.sessions = sessions;
        this.events = events;
        this.outcome = outcome;
        this.participants = participants;
    }

    /**
     * The assessment for the claim this participant has just filed.
     *
     * <p>Gated on the portal having actually recorded a submission, and counted from the event log
     * rather than taken on the browser's word. That makes a reload show the same thing, and it
     * means the assessment cannot be reached by typing the URL - someone who sees "your claim was
     * assessed" without having filed one has been told something untrue by the study.
     */
    @GetMapping("/outcome")
    public Outcome outcome() {
        String participantId = participants.currentRef();
        StudySession session = sessions.findFirstByParticipantIdOrderByStartedAtDesc(participantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "No open study session for participant " + participantId));

        // SERVER only: the portal's own sink writes these, and the browser cannot forge one (see
        // EventRepository). Counting client events would let anyone with the developer console
        // unlock the assessment without filing anything.
        if (events.countBySessionIdAndTypeAndSource(session.getId(), CLAIM_SUBMITTED, Event.SERVER) == 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "No claim has been submitted yet for participant " + participantId);
        }
        return new Outcome(outcome.scripted());
    }

    /**
     * {@code scripted} is configuration/study/outcome.json, passed through untouched - banner,
     * detail, the assessment panel and the escalation wording. The backend does not reshape it, so
     * correcting what a participant reads is a file edit and a restart.
     *
     * <p>No condition in here, in either factor. The participant's screens render identically in
     * all four cells; the only thing that differs between them is what they hear on the call.
     */
    public record Outcome(Map<String, Object> scripted) {
    }
}
