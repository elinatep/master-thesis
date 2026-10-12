package solutions.andreas.study;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TranscriptEntryRepository extends JpaRepository<TranscriptEntry, Long> {

    /**
     * The AI leg of one participant's call, in the order it was said.
     *
     * <p>Read by the agent console before a pickup. The agent has to open the call already knowing
     * what the participant told the assistant - {@code human-base.md} requires them to name a
     * specific detail back - and that continuity is held constant across all four cells, so this
     * is not condition-dependent in any way.
     */
    List<TranscriptEntry> findBySessionIdOrderByClientSeqAsc(Long sessionId);
}
