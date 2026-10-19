package solutions.andreas.study;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StudySessionRepository extends JpaRepository<StudySession, Long> {

    /** Delete a participant's sessions; their events + transcripts go via the FK cascade. */
    long deleteByParticipantId(String participantId);

    /**
     * The participant's current run - their most recently opened session - which is what a
     * server-emitted portal event hangs off. Empty if they have never opened one.
     */
    Optional<StudySession> findFirstByParticipantIdOrderByStartedAtDesc(String participantId);

    /**
     * The participant's run that is still going, if there is one.
     *
     * <p>Used to make opening a session idempotent. Every page load runs the gate, and a reload is
     * a page load: without this, refreshing the browser started a second session, and because
     * everything downstream reads "the most recent session", the participant's claim, their open
     * handover and their place in the queue all belonged to the one before it.
     */
    Optional<StudySession> findFirstByParticipantIdAndCompletedFalseOrderByStartedAtDesc(String participantId);
}
