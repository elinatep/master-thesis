package solutions.andreas.study;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.Map;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * A generic behavioural event (behavioural schema). The common, analysis-critical fields are
 * real columns; the variable, type-specific payload lives in {@code data} (JSONB) so new event
 * types need no migration. {@code clientSeq} orders events within a session despite network
 * reordering; {@code clientTs} is when it happened in the browser, {@code serverTs} when stored.
 *
 * <p>{@code source} says who emitted it. {@link #CLIENT} events come from the browser and carry a
 * {@code clientSeq}; {@link #SERVER} events are emitted by the portal's own domain services (via
 * {@code PortalEventSink}), have no client sequence, and order by {@code serverTs}. The server
 * stream is the parity-clean one — identical across all four cells of the design.
 */
@Entity
@Table(name = "event", schema = "behavioural")
public class Event {

    /** Emitted by the browser; ordered within its session by {@code clientSeq}. */
    public static final String CLIENT = "CLIENT";

    /** Emitted by a portal domain service; no client sequence, ordered by {@code serverTs}. */
    public static final String SERVER = "SERVER";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "session_id", nullable = false, updatable = false)
    private Long sessionId;

    // Null for SERVER events: the browser's counter has no meaning for them.
    @Column(name = "client_seq", updatable = false)
    private Long clientSeq;

    @Column(nullable = false, updatable = false)
    private String source;

    @Column(nullable = false, updatable = false)
    private String type;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(updatable = false)
    private Map<String, Object> data;

    @Column(name = "client_ts", updatable = false)
    private OffsetDateTime clientTs;

    @CreationTimestamp
    @Column(name = "server_ts", nullable = false, updatable = false)
    private OffsetDateTime serverTs;

    protected Event() {
    }

    public Event(Long sessionId, Long clientSeq, String type, Map<String, Object> data, OffsetDateTime clientTs,
            String source) {
        this.sessionId = sessionId;
        this.clientSeq = clientSeq;
        this.type = type;
        this.data = data;
        this.clientTs = clientTs;
        this.source = source;
    }

    /** A server-emitted domain event: no client sequence, no client timestamp. */
    public static Event server(Long sessionId, String type, Map<String, Object> data) {
        return new Event(sessionId, null, type, data, null, SERVER);
    }

    public Long getId() {
        return id;
    }

    public Long getSessionId() {
        return sessionId;
    }

    public Long getClientSeq() {
        return clientSeq;
    }

    public String getSource() {
        return source;
    }

    public String getType() {
        return type;
    }

    public Map<String, Object> getData() {
        return data;
    }

    public OffsetDateTime getClientTs() {
        return clientTs;
    }

    public OffsetDateTime getServerTs() {
        return serverTs;
    }
}
