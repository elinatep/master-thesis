package solutions.andreas.study;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

/**
 * Sits in front of the portal's own claim endpoint and decides, per arm, whether this submission
 * is allowed to succeed.
 *
 * <p>This is how the S4 arm makes the portal fail without the portal being taught to fail. The
 * portal's claim service is untouched and still behaves correctly; the study simply does not let
 * the request reach it, and answers with the error text from the arm file. As far as the
 * participant is concerned the website broke, which is exactly the manipulation - and as far as
 * Experiment 3 is concerned nothing about the portal has changed.
 *
 * <p>Every attempt is logged here, before the decision, with the body the participant submitted.
 * What people type - and how it changes when they are made to type it again - is part of what the
 * pre-test is looking at, and the portal's own CLAIM_SUBMITTED event cannot capture it for a
 * submission that was never allowed through.
 *
 * <p>The attempt number comes from the logged attempts, not from the browser. In S4 the number of
 * failures IS the irritation manipulation, so a reload, a back button or a second tab must not be
 * able to change it.
 */
@Component
@Order(0)
public class ClaimAttemptFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(ClaimAttemptFilter.class);

    /** POST /api/policies/{id}/claims — the portal's file-a-claim endpoint. */
    private static final Pattern FILE_CLAIM = Pattern.compile("^/api/policies/\\d+/claims$");

    /** Behavioural event type for one claim submission. Counting these gives the attempt number. */
    static final String CLAIM_ATTEMPT = "CLAIM_ATTEMPT";

    private final StudySessionRepository sessions;
    private final EventRepository events;
    private final ArmCatalogue arms;
    private final ObjectMapper mapper;

    public ClaimAttemptFilter(StudySessionRepository sessions, EventRepository events,
            ArmCatalogue arms, ObjectMapper mapper) {
        this.sessions = sessions;
        this.events = events;
        this.arms = arms;
        this.mapper = mapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !("POST".equalsIgnoreCase(request.getMethod())
                && FILE_CLAIM.matcher(request.getRequestURI()).matches());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {

        // Buffer the body up front: it has to be read here to be logged, and still be readable by
        // the portal's controller afterwards.
        BufferedRequest buffered = new BufferedRequest(request);

        String participantId = header(request, HeaderParticipantRefProvider.HEADER);
        StudySession session = participantId == null ? null
                : sessions.findFirstByParticipantIdOrderByStartedAtDesc(participantId).orElse(null);

        // No session means this is not a participant run (a researcher poking the API, a test).
        // Not the filter's business to decide who may file claims - let the portal answer.
        if (session == null) {
            chain.doFilter(buffered, response);
            return;
        }

        Arm arm = session.getArm();
        int attemptNumber = events.countBySessionIdAndType(session.getId(), CLAIM_ATTEMPT) + 1;
        logAttempt(session, arm, attemptNumber, buffered.body());

        Map<String, Object> scripted = arms.attempt(arm, attemptNumber);
        if (!"ERROR".equals(scripted.get("result"))) {
            log.info("Claim attempt {}/{} participant={} arm={} -> allowed through to the portal",
                    attemptNumber, arms.attemptCount(arm), participantId, arm.id());
            chain.doFilter(buffered, response);
            return;
        }

        log.info("Claim attempt {}/{} participant={} arm={} -> failed by script",
                attemptNumber, arms.attemptCount(arm), participantId, arm.id());
        writeScriptedFailure(response, scripted);
    }

    private void logAttempt(StudySession session, Arm arm, int attemptNumber, String body) {
        Map<String, Object> data = new HashMap<>();
        data.put("attempt", attemptNumber);
        data.put("arm", arm.id());
        data.put("submitted", parseOrRaw(body));
        events.save(new Event(session.getId(), null, CLAIM_ATTEMPT, data,
                OffsetDateTime.now(), Event.SERVER));
    }

    /**
     * The portal's frontend puts whatever message comes back in its own error alert, so the arm's
     * wording reaches the participant in the portal's own voice. The study's takeover screen then
     * picks the event up and adds the retry framing.
     */
    private void writeScriptedFailure(HttpServletResponse response, Map<String, Object> scripted)
            throws IOException {
        Object detail = scripted.get("bannerDetail");
        response.setStatus(HttpStatus.INTERNAL_SERVER_ERROR.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(mapper.writeValueAsString(Map.of(
                "status", 500,
                "error", "Internal Server Error",
                "message", detail == null ? "The claim could not be submitted." : detail.toString())));
    }

    private Object parseOrRaw(String body) {
        if (body == null || body.isBlank()) {
            return Map.of();
        }
        try {
            return mapper.readValue(body, new tools.jackson.core.type.TypeReference<Map<String, Object>>() {});
        } catch (RuntimeException e) {
            return Map.of("raw", body);
        }
    }

    private static String header(HttpServletRequest request, String name) {
        String v = request.getHeader(name);
        return v == null || v.isBlank() ? null : v.trim();
    }

    /** Reads the body once, keeps it, and replays it to whatever runs next. */
    private static final class BufferedRequest extends HttpServletRequestWrapper {

        private final byte[] body;

        BufferedRequest(HttpServletRequest request) throws IOException {
            super(request);
            this.body = request.getInputStream().readAllBytes();
        }

        String body() {
            return new String(body, StandardCharsets.UTF_8);
        }

        @Override
        public ServletInputStream getInputStream() {
            ByteArrayInputStream in = new ByteArrayInputStream(body);
            return new ServletInputStream() {
                @Override
                public int read() {
                    return in.read();
                }

                @Override
                public boolean isFinished() {
                    return in.available() == 0;
                }

                @Override
                public boolean isReady() {
                    return true;
                }

                @Override
                public void setReadListener(ReadListener listener) {
                    throw new UnsupportedOperationException("Async reads are not used here");
                }
            };
        }

        @Override
        public java.io.BufferedReader getReader() {
            return new java.io.BufferedReader(new java.io.InputStreamReader(
                    new ByteArrayInputStream(body), StandardCharsets.UTF_8));
        }
    }
}
