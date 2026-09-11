package solutions.andreas.study;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AgentHandoverRepository extends JpaRepository<AgentHandover, Long> {

    /** The queue an agent console works from: still waiting, longest wait first. */
    List<AgentHandover> findByPickedUpAtIsNullOrderByRequestedAtAsc();

    /**
     * The participant's current transfer, if any. Used to make the enqueue idempotent: a retried
     * tool call, or a reconnect after a dropped data channel, must not put the same participant in
     * the queue twice.
     */
    Optional<AgentHandover> findFirstBySessionIdAndEndedAtIsNullOrderByRequestedAtDesc(Long sessionId);

    long deleteByParticipantId(String participantId);
}
