package solutions.andreas.study;

import org.springframework.data.jpa.repository.JpaRepository;

public interface EventRepository extends JpaRepository<Event, Long> {

    /**
     * How many events of a type this session has logged. Used to derive the participant's claim
     * attempt number from the log itself, so the S4 arm fails exactly as many times as it
     * specifies no matter what the browser does - reload, back button, second tab.
     */
    int countBySessionIdAndType(Long sessionId, String type);
}
