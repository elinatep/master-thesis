package solutions.andreas.study;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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
    private final String secret;
    private final Duration ttl;

    public HandoverController(HandoverRepository handovers,
            @Value("${app.handover.secret:}") String secret,
            @Value("${app.handover.ttl:24h}") Duration ttl) {
        this.handovers = handovers;
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
        String token = generateToken();
        Condition condition = new Condition(req.aiAck(), req.humanAck());
        Handover h = new Handover(token, req.participantId().trim(), condition,
                blankToNull(req.name()), blankToNull(req.callbackUrl()), OffsetDateTime.now().plus(ttl));
        handovers.save(h);
        log.info("Handover registered: participant={} cell={} name={} expires={}",
                h.getParticipantId(), condition.cell(), h.getFullName(), h.getExpiresAt());
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
        Condition c = h.getCondition();
        log.info("Handover exchanged: participant={} cell={}", h.getParticipantId(), c.cell());
        return ResponseEntity.ok(new ExchangeResponse(
                h.getParticipantId(), c.aiAck(), c.humanAck(), h.getFullName(), h.getCallbackUrl()));
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
     * Both factors are required and have no default. An absent factor is a 400 rather than a
     * silent {@code false}: a dropped condition would put the participant in a cell nobody
     * assigned them to, and that corruption is invisible once the run is over.
     */
    public record RegisterRequest(
            @NotBlank String participantId,
            @NotNull Boolean aiAck,
            @NotNull Boolean humanAck,
            String name,
            String callbackUrl) {
    }

    public record RegisterResponse(String token, OffsetDateTime expiresAt) {
    }

    public record ExchangeResponse(String participantId, boolean aiAck, boolean humanAck,
            String name, String callbackUrl) {
    }
}
