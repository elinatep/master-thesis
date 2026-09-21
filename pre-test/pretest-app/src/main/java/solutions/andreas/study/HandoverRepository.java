package solutions.andreas.study;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HandoverRepository extends JpaRepository<Handover, Long> {

    Optional<Handover> findByToken(String token);
}
