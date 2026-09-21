package solutions.andreas.study;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Qualtrics handover. Two-step, so nothing sensitive travels in the redirect URL:
 * <ol>
 *   <li><b>Register</b> — Qualtrics (server-side) POSTs the participant's condition here and gets a
 *       short-lived opaque token.</li>
 *   <li><b>Exchange</b> — Qualtrics redirects the participant to the portal with {@code ?t=};
 *       the browser GETs {@code /api/handover/{token}} to recover the condition.</li>
 * </ol>
 * The register call is optionally guarded by a shared secret ({@code app.handover.secret}); the
 * token is reusable within its TTL ({@code app.handover.ttl}) so a browser reload can re-exchange.
 */
@RestController
@RequestMapping("/api/handover")
public class HandoverController {

    private static final Logger log = LoggerFactory.getLogger(HandoverController.class);
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Base64.Encoder TOKEN_ENCODER = Base64.getUrlEncoder().withoutPadding();

    private final HandoverRepository handovers;
    private final ArmCatalogue arms;
    private final String secret;
    private final Duration ttl;

    public HandoverController(HandoverRepository handovers, ArmCatalogue arms,
            @Value("${app.handover.secret:}") String secret,
            @Value("${app.handover.ttl:24h}") Duration ttl) {
        this.handovers = handovers;
        this.arms = arms;
        this.secret = secret;
        this.ttl = ttl;
    }

    /** Register a handover; returns the token Qualtrics puts on the redirect URL. */
    @PostMapping
    public RegisterResponse register(
            @RequestHeader(name = "X-Handover-Secret", required = false) String providedSecret,
            @Valid @RequestBody RegisterRequest req) {
        if (!secret.isBlank() && !secret.equals(providedSecret)) {
            log.warn("Handover register rejected: invalid/missing secret (participant={})", req.participantId());
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid or missing handover secret");
        }
        if (!arms.isKnown(req.arm())) {
            log.warn("Handover register rejected: unknown arm '{}' (participant={})",
                    req.arm(), req.participantId());
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Unknown arm '" + req.arm() + "'; configured arms are " + arms.known());
        }
        String token = generateToken();
        Arm arm = new Arm(req.arm());
        Handover h = new Handover(token, req.participantId().trim(), arm,
                blankToNull(req.name()), blankToNull(req.callbackUrl()), OffsetDateTime.now().plus(ttl));
        handovers.save(h);
        log.info("Handover registered: participant={} arm={} name={} expires={}",
                h.getParticipantId(), arm.id(), h.getFullName(), h.getExpiresAt());
        return new RegisterResponse(token, h.getExpiresAt());
    }

    /** Exchange a token for the condition. Unknown or expired token => 404 (fail loud at the gate). */
    @GetMapping("/{token}")
    public ResponseEntity<ExchangeResponse> exchange(@PathVariable String token) {
        Handover h = handovers.findByToken(token).orElse(null);
        if (h == null) {
            log.warn("Handover exchange failed: unknown token");
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        if (h.isExpired()) {
            log.warn("Handover exchange failed: expired token (participant={}, expired={})",
                    h.getParticipantId(), h.getExpiresAt());
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        Arm a = h.getArm();
        log.info("Handover exchanged: participant={} arm={}", h.getParticipantId(), a.id());
        return ResponseEntity.ok(new ExchangeResponse(
                h.getParticipantId(), a.id(), h.getFullName(), h.getCallbackUrl()));
    }

    private static String generateToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return TOKEN_ENCODER.encodeToString(bytes);
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    // ---- payloads ------------------------------------------------------------

    /**
     * The arm is required and is checked against the configured arms. Neither a missing nor an
     * unrecognised arm is coerced to a default: a participant assigned to a condition nobody
     * defined looks exactly like real data, and is typically noticed - if at all - during analysis.
     */
    public record RegisterRequest(
            @NotBlank String participantId,
            @NotBlank String arm,
            String name,
            String callbackUrl) {
    }

    public record RegisterResponse(String token, OffsetDateTime expiresAt) {
    }

    public record ExchangeResponse(String participantId, String arm, String name,
            String callbackUrl) {
    }
}
