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
 * A pre-registered Qualtrics handover (behavioural schema). Qualtrics registers the participant's
 * condition server-side and gets back the {@code token}; the portal exchanges the token on entry to
 * recover the condition (participant id, the two acknowledgement factors, display name, callback
 * URL). Reusable within its TTL - {@code expiresAt} bounds it - so a browser reload can re-exchange
 * cleanly.
 */
@Entity
@Table(name = "handover", schema = "behavioural")
public class Handover {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, updatable = false)
    private String token;

    @Column(name = "participant_id", nullable = false, updatable = false)
    private String participantId;

    @Column(name = "ai_ack", nullable = false, updatable = false)
    private boolean aiAck;

    @Column(name = "human_ack", nullable = false, updatable = false)
    private boolean humanAck;

    @Column(name = "full_name", updatable = false)
    private String fullName;

    @Column(name = "callback_url", updatable = false)
    private String callbackUrl;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private OffsetDateTime expiresAt;

    protected Handover() {
    }

    public Handover(String token, String participantId, Condition condition, String fullName,
            String callbackUrl, OffsetDateTime expiresAt) {
        this.token = token;
        this.participantId = participantId;
        this.aiAck = condition.aiAck();
        this.humanAck = condition.humanAck();
        this.fullName = fullName;
        this.callbackUrl = callbackUrl;
        this.expiresAt = expiresAt;
    }

    public boolean isExpired() {
        return expiresAt.isBefore(OffsetDateTime.now());
    }

    public String getParticipantId() {
        return participantId;
    }

    /** The assigned cell of the 2x2. */
    public Condition getCondition() {
        return new Condition(aiAck, humanAck);
    }

    public String getFullName() {
        return fullName;
    }

    public String getCallbackUrl() {
        return callbackUrl;
    }

    public OffsetDateTime getExpiresAt() {
        return expiresAt;
    }
}
