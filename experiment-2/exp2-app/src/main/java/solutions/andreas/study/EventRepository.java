package solutions.andreas.study;

import org.springframework.data.jpa.repository.JpaRepository;

public interface EventRepository extends JpaRepository<Event, Long> {

    /**
     * How many events of one type and source this session has logged.
     *
     * <p>Used to tell whether the participant has actually filed their claim, by counting the
     * portal's own {@code CLAIM_SUBMITTED} with {@code source = SERVER}.
     *
     * <p>Source, not just type, on purpose. The browser can post events of any type it likes to
     * {@code /api/study/events} - that endpoint has to stay open, because participants arrive
     * anonymously - so counting client events would let anyone with the developer console unlock
     * the assessment without filing a claim. Only the portal's sink writes SERVER rows, and only
     * a real submission makes it do so.
     */
    long countBySessionIdAndTypeAndSource(Long sessionId, String type, String source);
}
