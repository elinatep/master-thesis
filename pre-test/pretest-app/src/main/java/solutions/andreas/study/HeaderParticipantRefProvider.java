package solutions.andreas.study;

import solutions.andreas.portal.core.spi.AccountRefProvider;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/**
 * The study's answer to "which account is this request for": the participant id the frontend
 * attaches to every business call, recovered from the Qualtrics handover at intake.
 *
 * <p>This is the study side of {@link AccountRefProvider} — it replaces the portal's fallback simply
 * by existing (see {@code PortalDefaultsConfig}). Keeping it here is what lets the portal scope its
 * data per participant without ever knowing that participants, handovers or Qualtrics exist.
 *
 * <p>Fails loud rather than returning null, so a business call made without the header reports the
 * header it wanted instead of a generic error.
 */
@Component
public class HeaderParticipantRefProvider implements AccountRefProvider {

    public static final String HEADER = "X-Participant-Id";

    private final HttpServletRequest request;

    // Spring injects a request-scoped proxy here, so this singleton always sees the live request.
    public HeaderParticipantRefProvider(HttpServletRequest request) {
        this.request = request;
    }

    @Override
    public String currentRef() {
        String pid = request.getHeader(HEADER);
        if (pid == null || pid.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing " + HEADER + " header");
        }
        return pid.trim();
    }
}
