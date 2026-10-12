package solutions.andreas.study;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Puts passwords on the two staff-facing parts of this study, and on nothing else.
 *
 * <p>The study itself is open by design: participants arrive anonymously from Prolific, so there
 * can be no login in front of the portal, and Easy Auth has to stay off for the same reason. Every
 * participant is redirected to this host, so the URL is not a secret - which means anything left
 * unprotected is effectively public.
 *
 * <p>Two credentials, not one, and the split is the point rather than tidiness:
 *
 * <ul>
 *   <li><b>researcher</b> - the data view, the log endpoint behind it, and the admin endpoints,
 *       one of which deletes every participant's data. Without this, any participant could read
 *       every other participant's Prolific ID, behavioural log and voice transcript, or wipe the
 *       study mid-run.</li>
 *   <li><b>agent</b> - the human confederate's console and queue. They need to see who is waiting
 *       and what the AI assistant already heard; they do not need to be able to delete the study.
 *       Several people may run sessions and share this credential - which agent handled which
 *       session comes from their own session log, not from who was logged in.</li>
 * </ul>
 *
 * <p>Everything else - the SPA, the portal's API, the handover endpoints, the voice token - stays
 * open, because that is the study. The handover register call is guarded by its own shared secret
 * instead (see {@link HandoverController}), because Qualtrics calls it server-to-server and cannot
 * hold a password.
 */
@Configuration
public class StudySecurityConfig {

    private static final Logger log = LoggerFactory.getLogger(StudySecurityConfig.class);

    /**
     * The researcher's paths: the data page, the endpoint that feeds it, and the admin surface.
     *
     * <p>The page as well as the endpoint, not just the endpoint - protecting only the API would
     * still serve the page, which would then fail its fetch and look broken rather than locked.
     *
     * <p>{@code /api/admin/**} is here because {@code /api/admin/reset/all} deletes every
     * participant's data and had nothing in front of it at all. In the pre-test that was a
     * controlled, supervised run; this study runs unattended for weeks with the URL in the hands
     * of four hundred strangers.
     */
    static final String[] RESEARCHER_PATHS =
            { "/data", "/data/**", "/api/study/log", "/api/admin/**" };

    /**
     * The human agent's paths: the queue they poll and the console page that shows it.
     *
     * <p>{@code /agent} and {@code /agent/**} are listed before the console page exists. That is
     * deliberate: a page added later to a path nobody protected is how a queue full of participant
     * ids and transcripts ends up public, and the omission is invisible. Protecting the path now
     * means the page is born behind the credential.
     */
    static final String[] AGENT_PATHS = { "/agent", "/agent/**", "/api/agent/**" };

    private final String researcherUsername;
    private final String researcherPassword;
    private final String agentUsername;
    private final String agentPassword;

    public StudySecurityConfig(
            @Value("${app.researcher.username:researcher}") String researcherUsername,
            @Value("${app.researcher.password:}") String researcherPassword,
            @Value("${app.agent.username:agent}") String agentUsername,
            @Value("${app.agent.password:}") String agentPassword) {
        this.researcherUsername = researcherUsername;
        this.researcherPassword = researcherPassword;
        this.agentUsername = agentUsername;
        this.agentPassword = agentPassword;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        // CSRF off: the participant-facing API is called by the SPA with no session and no cookie,
        // and the portal library predates this config entirely. Turning it on here would reject
        // every claim submission in the study - a protection against an attack this app has no
        // surface for, at the cost of the app.
        http.csrf(csrf -> csrf.disable());

        boolean haveResearcher = !researcherPassword.isBlank();
        boolean haveAgent = !agentPassword.isBlank();

        // A missing password closes its paths rather than opening them. A deployment that forgot
        // one is a deployment that forgot, and the safe reading of that is "nobody may" rather
        // than "everybody may". Neither case stops the application starting: a staff-page
        // misconfiguration must not take a running study down, and the participant-facing half is
        // unaffected either way.
        if (!haveResearcher) {
            log.warn("RESEARCHER_PASSWORD is not set. {} are CLOSED - nobody can read the "
                    + "behavioural data or reset anything through the web until it is set and the "
                    + "app redeployed.", String.join(", ", RESEARCHER_PATHS));
        }
        if (!haveAgent) {
            log.warn("AGENT_PASSWORD is not set. {} are CLOSED - nobody can pick up a handover, "
                    + "which is the second half of this study. Set it before a session.",
                    String.join(", ", AGENT_PATHS));
        }

        http.authorizeHttpRequests(auth -> {
            if (haveResearcher) {
                auth.requestMatchers(RESEARCHER_PATHS).hasRole("RESEARCHER");
            } else {
                auth.requestMatchers(RESEARCHER_PATHS).denyAll();
            }
            if (haveAgent || haveResearcher) {
                // The researcher is allowed into the agent console as well, so a session can be
                // run or rescued without a second browser profile. The reverse is not true.
                auth.requestMatchers(AGENT_PATHS).hasAnyRole("AGENT", "RESEARCHER");
            } else {
                auth.requestMatchers(AGENT_PATHS).denyAll();
            }
            auth.anyRequest().permitAll();
        });

        if (haveResearcher || haveAgent) {
            http.httpBasic(Customizer.withDefaults());
        }
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * Accounts from the environment. There are two readers of this deployment and no reason for it
     * to own a user table.
     *
     * <p>An account with a blank password is not created at all rather than created open: an
     * account that exists is an account that can be guessed into.
     */
    @Bean
    public UserDetailsService userDetailsService(PasswordEncoder encoder) {
        List<UserDetails> users = new ArrayList<>();
        if (!researcherPassword.isBlank()) {
            log.info("Researcher paths are password-protected (user '{}')", researcherUsername);
            users.add(User.withUsername(researcherUsername)
                    .password(encoder.encode(researcherPassword))
                    .roles("RESEARCHER")
                    .build());
        }
        if (!agentPassword.isBlank()) {
            if (agentUsername.equals(researcherUsername)) {
                // Two accounts cannot share a username: InMemoryUserDetailsManager keys on it, so
                // one would silently shadow the other and the agent would end up with whichever
                // role was registered last. Refusing is better than a deployment where the
                // confederate can wipe the database because two usernames matched.
                throw new IllegalStateException(
                        "AGENT_USER and RESEARCHER_USER are both '" + agentUsername + "'. They "
                        + "must differ: the whole point of the two accounts is that the agent "
                        + "cannot reach the researcher's paths.");
            }
            log.info("Agent console is password-protected (user '{}')", agentUsername);
            users.add(User.withUsername(agentUsername)
                    .password(encoder.encode(agentPassword))
                    .roles("AGENT")
                    .build());
        }
        return new InMemoryUserDetailsManager(users);
    }
}
