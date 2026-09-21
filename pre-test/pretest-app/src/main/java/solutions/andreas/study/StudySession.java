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
 * One participant run of the portal (behavioural schema). Created at intake from the Qualtrics
 * handoff; {@code participantId} is the pseudonymous key every logged event hangs off, and
 * {@code arm} is the version of the claim outcome they were shown. Closed out on completion
 * (handback).
 */
@Entity
@Table(name = "study_session", schema = "behavioural")
public class StudySession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "participant_id", nullable = false, updatable = false)
    private String participantId;

    @Column(nullable = false, updatable = false)
    private String arm;

    @Column(name = "user_agent", updatable = false)
    private String userAgent;

    @CreationTimestamp
    @Column(name = "started_at", nullable = false, updatable = false)
    private OffsetDateTime startedAt;

    @Column(name = "ended_at")
    private OffsetDateTime endedAt;

    @Column(nullable = false)
    private boolean completed = false;

    @Column(name = "task_success")
    private Boolean taskSuccess;

    protected StudySession() {
    }

    public StudySession(String participantId, Arm arm, String userAgent) {
        this.participantId = participantId;
        this.arm = arm.id();
        this.userAgent = userAgent;
    }

    public Long getId() {
        return id;
    }

    public String getParticipantId() {
        return participantId;
    }

    /** The arm this participant was assigned to. */
    public Arm getArm() {
        return new Arm(arm);
    }

    public OffsetDateTime getStartedAt() {
        return startedAt;
    }

    public OffsetDateTime getEndedAt() {
        return endedAt;
    }

    public void setEndedAt(OffsetDateTime endedAt) {
        this.endedAt = endedAt;
    }

    public boolean isCompleted() {
        return completed;
    }

    public void setCompleted(boolean completed) {
        this.completed = completed;
    }

    public Boolean getTaskSuccess() {
        return taskSuccess;
    }

    public void setTaskSuccess(Boolean taskSuccess) {
        this.taskSuccess = taskSuccess;
    }
}
