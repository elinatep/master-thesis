package solutions.andreas.study;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Puts a password on the researcher's view of the data, and on nothing else.
 *
 * <p>The study is open by design: participants arrive anonymously from Prolific, so there can be no
 * login in front of the portal, and Easy Auth has to stay off for the same reason. That leaves
 * {@code /data} and the endpoint behind it reachable by anyone who knows the host - and every
 * participant knows the host, because they are redirected to it. Without this, any of them could
 * read every other participant's Prolific ID and complete behavioural log. Those IDs are
 * pseudonymous personal data, so that is an ethics question rather than an untidiness.
 *
 * <p>Only two paths are protected. Everything else - the SPA, the portal's API, the handover
 * endpoints - stays open, because that is the study.
 */
@Configuration
public class ResearcherSecurityConfig {

    private static final Logger log = LoggerFactory.getLogger(ResearcherSecurityConfig.class);

    /**
     * The researcher's data view: the page and the endpoint that feeds it. Both, not just the
     * endpoint - protecting only the API would still serve the page, which would then fail its
     * fetch and look broken rather than locked.
     */
    static final String[] RESEARCHER_PATHS = { "/data", "/data/**", "/api/study/log" };

    private final String username;
    private final String password;

    public ResearcherSecurityConfig(
            @Value("${app.researcher.username:researcher}") String username,
            @Value("${app.researcher.password:}") String password) {
        this.username = username;
        this.password = password;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        // CSRF off: the participant-facing API is called by the SPA with no session and no cookie,
        // and the portal library predates this config entirely. Turning it on here would reject
        // every claim submission in the study - a protection against an attack this app has no
        // surface for, at the cost of the app.
        http.csrf(csrf -> csrf.disable());

        if (password.isBlank()) {
            // Closed, not open. A missing password is a deployment that forgot to set one, and the
            // safe reading of that is "nobody may read the data" rather than "everybody may". The
            // participant-facing study keeps running either way, which is why this does not simply
            // refuse to start: a researcher-page misconfiguration must not take the study down
            // mid-run.
            log.warn("RESEARCHER_PASSWORD is not set. {} are CLOSED - nobody can read the "
                    + "behavioural data through the web until it is set and the app redeployed.",
                    String.join(", ", RESEARCHER_PATHS));
            http.authorizeHttpRequests(auth -> auth
                    .requestMatchers(RESEARCHER_PATHS).denyAll()
                    .anyRequest().permitAll());
            return http.build();
        }

        log.info("Researcher data view is password-protected (user '{}')", username);
        http.authorizeHttpRequests(auth -> auth
                .requestMatchers(RESEARCHER_PATHS).authenticated()
                .anyRequest().permitAll());
        http.httpBasic(Customizer.withDefaults());
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * One in-memory account, from the environment. There is exactly one reader of this data and no
     * reason for the deployment to own a user table.
     */
    @Bean
    public UserDetailsService userDetailsService(PasswordEncoder encoder) {
        if (password.isBlank()) {
            // No account at all rather than one with an empty password: an account that exists is
            // an account that can be guessed into.
            return new InMemoryUserDetailsManager();
        }
        return new InMemoryUserDetailsManager(User.withUsername(username)
                .password(encoder.encode(password))
                .roles("RESEARCHER")
                .build());
    }
}
