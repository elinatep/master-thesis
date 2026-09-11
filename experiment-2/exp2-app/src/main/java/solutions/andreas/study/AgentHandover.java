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
 * One AI-to-human transfer (behavioural schema): the participant is waiting, and this is what the
 * human agent is told before they pick up.
 *
 * <p>{@code summary} is the argument object of the assistant's {@code transfer_to_human_agent}
 * call - what the customer is disputing, what they are asking for, the grounds they gave, and how
 * they have come across. It is sent in full in all four cells: handing over context is
 * <em>continuity</em>, which the design holds constant, not <em>acknowledgement</em>, which it
 * manipulates. Withholding it in the no-acknowledgement cells would confound the two factors
 * beyond separating.
 *
 * <p>The assigned condition is not copied here. It lives on {@link StudySession} and the console
 * joins to it, so nothing an agent does during a call can touch it.
 */
@Entity
@Table(name = "agent_handover", schema = "behavioural")
public class AgentHandover {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "session_id", nullable = false, updatable = false)
    private Long sessionId;

    @Column(name = "participant_id", nullable = false, updatable = false)
    private String participantId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(updatable = false)
    private Map<String, Object> summary;

    @CreationTimestamp
    @Column(name = "requested_at", nullable = false, updatable = false)
    private OffsetDateTime requestedAt;

    @Column(name = "picked_up_at")
    private OffsetDateTime pickedUpAt;

    @Column(name = "agent_id")
    private String agentId;

    @Column(name = "ended_at")
    private OffsetDateTime endedAt;

    protected AgentHandover() {
    }

    public AgentHandover(Long sessionId, String participantId, Map<String, Object> summary) {
        this.sessionId = sessionId;
        this.participantId = participantId;
        this.summary = summary;
    }

    public Long getId() {
        return id;
    }

    public Long getSessionId() {
        return sessionId;
    }

    public String getParticipantId() {
        return participantId;
    }

    public Map<String, Object> getSummary() {
        return summary;
    }

    public OffsetDateTime getRequestedAt() {
        return requestedAt;
    }

    public OffsetDateTime getPickedUpAt() {
        return pickedUpAt;
    }

    public String getAgentId() {
        return agentId;
    }

    public void pickUp(String agentId) {
        this.agentId = agentId;
        this.pickedUpAt = OffsetDateTime.now();
    }

    public OffsetDateTime getEndedAt() {
        return endedAt;
    }

    public void end() {
        this.endedAt = OffsetDateTime.now();
    }
}
