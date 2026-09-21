package solutions.andreas.study;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Stamps the request's participant id into the logging MDC, so <em>every</em> log line emitted while
 * handling that request carries it (see {@code logging.pattern.level}) and one participant can be
 * followed end-to-end through the Azure logs.
 *
 * <p>Study-owned on purpose: tying log lines to a participant is behavioural-study plumbing, not
 * something the portal should know about. Runs outermost so the MDC is already set for every portal
 * filter and handler downstream, and is always cleared in a finally (the thread is pooled).
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ParticipantMdcFilter extends OncePerRequestFilter {

    private static final String MDC_KEY = "participantId";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String pid = request.getHeader(HeaderParticipantRefProvider.HEADER);
        if (pid != null && !pid.isBlank()) {
            MDC.put(MDC_KEY, pid.trim());
        }
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }
}
