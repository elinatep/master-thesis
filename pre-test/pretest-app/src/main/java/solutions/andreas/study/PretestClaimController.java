package solutions.andreas.study;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.time.OffsetDateTime;
import java.util.HashMap;
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

/**
 * The claim the participant files, and the outcome they are shown. This is the manipulation.
 *
 * <p>Named for the study rather than the domain, and deliberately: the portal library has its own
 * {@code ClaimController} for real claims, and two beans of that name cannot coexist. The name
 * also says the right thing - this one scripts a pre-test, it does not manage claims.
 *
 * <p><b>Why the outcome is scripted here rather than produced by the portal's claim domain.</b>
 * The portal models claims properly: a policy, a coverage table, a status that moves
 * SUBMITTED to IN_REVIEW to SETTLED. It would never return "5% is eligible, your documentation was
 * complete, no further explanation is provided", and it would certainly never fail twice with
 * E-4092. Those outcomes are experimental apparatus wearing a portal's clothes, so they live in
 * the study application and are read from {@code configuration/arms/}. The portal supplies the
 * chrome the participant sees and the event sink the study logs through; it is not asked to
 * pretend its own claims engine behaves in ways it does not.
 *
 * <p><b>Why the attempt count is server-side.</b> In the S4 arm the portal has to fail twice.
 * Counting attempts in the browser would let a reload, a back button, or a second tab hand the
 * participant a different number of failures than the arm specifies - and the irritation
 * manipulation is precisely the number of failures. The count is derived from the logged attempts
 * for the session, so it survives anything the participant does to the page.
 */
@RestController
@RequestMapping("/api/pretest")
public class PretestClaimController {

    private static final Logger log = LoggerFactory.getLogger(PretestClaimController.class);

    /** Behavioural event type for one claim submission. Counting these gives the attempt number. */
    static final String CLAIM_ATTEMPT = "CLAIM_ATTEMPT";

    private final StudySessionRepository sessions;
    private final EventRepository events;
    private final ArmCatalogue arms;
    private final AccountRefProvider participants;

    public PretestClaimController(StudySessionRepository sessions, EventRepository events, ArmCatalogue arms,
            AccountRefProvider participants) {
        this.sessions = sessions;
        this.events = events;
        this.arms = arms;
        this.participants = participants;
    }

    /**
     * The claim form as this participant should see it before their first submission: which attempt
     * they are on, and whether the arm has already failed them once.
     *
     * <p>Serving this rather than letting the browser assume "attempt 1" is what makes a reload
     * mid-S4 land the participant back where they were instead of restarting the script.
     */
    @GetMapping("/claim")
    public FormState formState() {
        StudySession session = currentSession();
        Arm arm = session.getArm();
        int submitted = attemptsSoFar(session);
        return new FormState(arm.id(), submitted + 1, arms.attemptCount(arm), submitted > 0
                ? arms.attempt(arm, submitted)
                : null);
    }

    /**
     * Submit the claim. Logs what the participant actually typed, then answers with whatever this
     * arm scripts for this attempt.
     *
     * <p>The submitted fields are logged verbatim before the outcome is decided. What people type
     * into the form - and how it changes on the forced retry in S4 - is part of what the pre-test
     * is looking at, and it would be lost if only the outcome were recorded.
     */
    @PostMapping("/claim")
    @Transactional
    public Outcome submit(@Valid @RequestBody ClaimSubmission submission) {
        StudySession session = currentSession();
        Arm arm = session.getArm();
        int attemptNumber = attemptsSoFar(session) + 1;

        Map<String, Object> logged = new HashMap<>();
        logged.put("attempt", attemptNumber);
        logged.put("arm", arm.id());
        logged.put("typeOfDamage", submission.typeOfDamage());
        logged.put("cause", submission.cause());
        logged.put("incidentDate", submission.incidentDate());
        logged.put("estimatedDamage", submission.estimatedDamage());
        events.save(new Event(session.getId(), null, CLAIM_ATTEMPT, logged,
                OffsetDateTime.now(), Event.SERVER));

        Map<String, Object> scripted = arms.attempt(arm, attemptNumber);
        boolean finalAttempt = attemptNumber >= arms.attemptCount(arm);
        log.info("Claim attempt {}/{} for participant={} arm={} -> {}",
                attemptNumber, arms.attemptCount(arm), session.getParticipantId(), arm.id(),
                scripted.get("result"));

        return new Outcome(arm.id(), attemptNumber, arms.attemptCount(arm), finalAttempt, scripted);
    }

    private StudySession currentSession() {
        String participantId = participants.currentRef();
        return sessions.findFirstByParticipantIdOrderByStartedAtDesc(participantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "No open study session for participant " + participantId));
    }

    private int attemptsSoFar(StudySession session) {
        return events.countBySessionIdAndType(session.getId(), CLAIM_ATTEMPT);
    }

    // ---- payloads ------------------------------------------------------------

    /**
     * What the participant typed. Deliberately not validated against the briefing: whether people
     * copy the given values correctly is an observation, not an error to prevent. Only presence is
     * required, so an empty submission cannot be mistaken for a completed one.
     */
    public record ClaimSubmission(
            @NotBlank String typeOfDamage,
            @NotBlank String cause,
            @NotBlank String incidentDate,
            @NotBlank String estimatedDamage) {
    }

    /** Where the participant is in the arm's script, before they have submitted anything. */
    public record FormState(String arm, int attemptNumber, int totalAttempts,
            Map<String, Object> previousOutcome) {
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
