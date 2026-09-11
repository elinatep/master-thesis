package solutions.andreas.study;

import solutions.andreas.portal.core.spi.PortalEvent;
import solutions.andreas.portal.core.spi.PortalEventSink;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Writes the portal's domain events into the behavioural schema, tying each to the participant's
 * current session. This is the study side of {@link PortalEventSink}; supplying it replaces the
 * portal's discarding default.
 *
 * <p>Why bother when the browser already logs these actions: the browser can only log what it does,
 * so at level 3 (voice-only, server-side tools, no GUI) the domain events would simply be missing.
 * These rows are written identically in all four conditions, which is what makes them the
 * parity-clean stream for analysis — filter {@code source = 'SERVER'}.
 *
 * <p>Never throws. It runs inside the business transaction that produced the event, and a lost log
 * line must not roll back a participant's claim.
 */
@Component
public class BehaviouralEventSink implements PortalEventSink {

    private static final Logger log = LoggerFactory.getLogger(BehaviouralEventSink.class);

    private final StudySessionRepository sessions;
    private final EventRepository events;
    private final HttpServletRequest request;

    // HttpServletRequest resolves to a request-scoped proxy, so this singleton sees the live request.
    public BehaviouralEventSink(StudySessionRepository sessions, EventRepository events,
            HttpServletRequest request) {
        this.sessions = sessions;
        this.events = events;
        this.request = request;
    }

    @Override
    public void record(PortalEvent event) {
        try {
            String participantId = participantId();
            if (participantId == null) {
                // Not a participant-scoped request (admin tooling, seeding): nothing to attach to.
                log.debug("Portal event {} not logged: no participant on the request", event.type());
                return;
            }
            var session = sessions.findFirstByParticipantIdOrderByStartedAtDesc(participantId);
            if (session.isEmpty()) {
                // Loud: a participant acting in the portal with no open session is a lost data point.
                log.warn("Portal event {} NOT logged: participant {} has no study session",
                        event.type(), participantId);
                return;
            }
            events.save(Event.server(session.get().getId(), event.type(), event.data()));
            log.debug("Logged portal event {} for participant {}", event.type(), participantId);
        } catch (RuntimeException e) {
            log.error("Failed to log portal event {}", event.type(), e);
        }
    }

    /** The participant on the current request, or null if there is no request or no header. */
    private String participantId() {
        try {
            String pid = request.getHeader(HeaderParticipantRefProvider.HEADER);
            return pid == null || pid.isBlank() ? null : pid.trim();
        } catch (IllegalStateException e) {
            return null; // no request bound (e.g. startup seeding)
        }
    }
}
