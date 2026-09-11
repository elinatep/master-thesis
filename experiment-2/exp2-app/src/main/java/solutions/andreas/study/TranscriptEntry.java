package solutions.andreas.study;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import org.hibernate.annotations.CreationTimestamp;

/**
 * One voice transcript line (behavioural schema). Voice-derived PERSONAL data: the audio is not
 * stored (it goes to OpenAI), only the recognised text, linked to the pseudonymous participant
 * via the session. Written as-it-happens, relayed from the Realtime data channel in the browser.
 */
@Entity
@Table(name = "transcript_entry", schema = "behavioural")
public class TranscriptEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "session_id", nullable = false, updatable = false)
    private Long sessionId;

    @Column(name = "client_seq", nullable = false, updatable = false)
    private long clientSeq;

    @Column(nullable = false, updatable = false)
    private String role;

    @Column(nullable = false, updatable = false)
    private String text;

    @Column(name = "client_ts", updatable = false)
    private OffsetDateTime clientTs;

    @CreationTimestamp
    @Column(name = "server_ts", nullable = false, updatable = false)
    private OffsetDateTime serverTs;

    protected TranscriptEntry() {
    }

    public TranscriptEntry(Long sessionId, long clientSeq, String role, String text, OffsetDateTime clientTs) {
        this.sessionId = sessionId;
        this.clientSeq = clientSeq;
        this.role = role;
        this.text = text;
        this.clientTs = clientTs;
    }

    public Long getId() {
        return id;
    }

    public Long getSessionId() {
        return sessionId;
    }

    public long getClientSeq() {
        return clientSeq;
    }

    public String getRole() {
        return role;
    }

    public String getText() {
        return text;
    }

    public OffsetDateTime getClientTs() {
        return clientTs;
    }

    public OffsetDateTime getServerTs() {
        return serverTs;
    }
}
