package solutions.andreas.study;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * The voice-bot study, hosting the insurance portal.
 *
 * <p>Note what is <em>not</em> here: no {@code scanBasePackages}, no {@code @EntityScan}, no
 * {@code @EnableJpaRepositories} pointing at the portal. This application scans only its own
 * package; the portal contributes itself through {@code PortalAutoConfiguration}, triggered by
 * having {@code portal-core} on the classpath. That is the whole integration contract, and it is
 * what lets the portal live in a separate repository under a package this application knows nothing
 * about.
 *
 * <p>The study supplies its side of the portal's extension points by declaring beans that implement
 * them — {@code HeaderParticipantRefProvider} for {@code AccountRefProvider},
 * {@code BehaviouralEventSink} for {@code PortalEventSink}. Each replaces the portal's fallback
 * simply by existing.
 */
@SpringBootApplication
public class StudyApplication {

    public static void main(String[] args) {
        SpringApplication.run(StudyApplication.class, args);
    }
}
