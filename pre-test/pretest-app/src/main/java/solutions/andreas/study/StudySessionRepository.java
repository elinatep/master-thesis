package solutions.andreas.study;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StudySessionRepository extends JpaRepository<StudySession, Long> {

    /** Delete a participant's sessions; their events go via the FK cascade. */
    long deleteByParticipantId(String participantId);

    /**
     * The participant's current run — their most recently opened session — which is what a
     * server-emitted portal event hangs off. Empty if they have never opened one.
     */
    Optional<StudySession> findFirstByParticipantIdOrderByStartedAtDesc(String participantId);
}
