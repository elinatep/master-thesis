package solutions.andreas.study;

import solutions.andreas.portal.core.user.AccountProvisioner;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Study intake: turns an arriving participant into a portal account. The frontend calls this once,
 * after opening the behavioural session, and the portal provisions (and seeds, if new) from there.
 *
 * <p>This is the study's half of the handshake — it knows about participants and the Qualtrics
 * handoff, and hands the portal only what the portal understands: an opaque account ref and a
 * display name. Idempotent, so a returning participant is left exactly as they were.
 */
@RestController
@RequestMapping("/api/study")
public class IntakeController {

    private static final Logger log = LoggerFactory.getLogger(IntakeController.class);

    private final AccountProvisioner accounts;

    public IntakeController(AccountProvisioner accounts) {
        this.accounts = accounts;
    }

    // The display name is the Qualtrics ?name= handoff, relayed as a header. Optional: a run without
    // it falls back to the shared persona name, so (unlike the participant id) it never fails intake.
    @PostMapping("/intake")
    public void intake(
            @RequestHeader(name = HeaderParticipantRefProvider.HEADER, required = false) String participantId,
            @RequestHeader(name = "X-Participant-Name", required = false) String name) {
        if (participantId == null || participantId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Missing " + HeaderParticipantRefProvider.HEADER + " header");
        }
        log.debug("Intake for participant {}", participantId);
        accounts.provision(participantId.trim(), decode(name));
    }

    /** The name header is URL-encoded by the frontend (headers are Latin-1 only); decode as UTF-8. */
    private static String decode(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return URLDecoder.decode(value, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return value; // not encoded as expected — use as-is rather than fail intake
        }
    }
}
