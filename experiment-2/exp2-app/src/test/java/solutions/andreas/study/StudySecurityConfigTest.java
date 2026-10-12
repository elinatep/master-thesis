package solutions.andreas.study;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcAutoConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * What this guards is a pair of opposite mistakes, both invisible in a browser that happens to be
 * logged in: locking the participants out of the study, and leaving their data open.
 *
 * <p>The second is the reason the config exists. The first is the reason it is easy to get wrong -
 * adding Spring Security to an application secures everything by default, so every
 * participant-facing path has to be permitted explicitly, and a later edit that drops one would
 * break the study for real participants while every test that only checks /data still passes.
 *
 * <p>The third thing it guards is the split between the two credentials. The confederate holds one
 * of them and talks to participants unsupervised; if that credential also opened
 * {@code /api/admin/reset/all}, a misclick would end the study.
 */
class StudySecurityConfigTest {

    private static final String REGISTER_BODY =
            "{\"participantId\":\"p1\",\"aiAck\":true,\"humanAck\":false}";

    /** A properly configured deployment: both credentials set, each reaching only its own paths. */
    @SpringBootTest
    @Import(MockMvcAutoConfiguration.class)
    @TestPropertySource(properties = {
            "app.researcher.username=researcher",
            "app.researcher.password=s3cret",
            "app.agent.username=agent",
            "app.agent.password=ag3nt",
    })
    static class WithBothPasswordsSet {

        @Autowired
        MockMvc mvc;

        @Test
        void dataPageDemandsAPassword() throws Exception {
            mvc.perform(get("/data")).andExpect(status().isUnauthorized());
        }

        @Test
        void behaviouralLogDemandsAPassword() throws Exception {
            mvc.perform(get("/api/study/log")).andExpect(status().isUnauthorized());
        }

        @Test
        void wrongPasswordIsRejected() throws Exception {
            mvc.perform(get("/api/study/log").with(httpBasic("researcher", "not-the-password")))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        void researcherCanReadTheLog() throws Exception {
            mvc.perform(get("/api/study/log").with(httpBasic("researcher", "s3cret")))
                    .andExpect(status().isOk());
        }

        /**
         * The endpoint that deletes every participant's data. Unauthenticated it was reachable by
         * anyone who found the URL - which is every participant, because they are redirected here.
         *
         * <p>Asserted by rejection rather than by calling it successfully: a test that proved the
         * researcher can wipe the database would have to wipe the database.
         */
        @Test
        void theResetEndpointDemandsAPassword() throws Exception {
            mvc.perform(post("/api/admin/reset/all")).andExpect(status().isUnauthorized());
        }

        /** The whole reason for two credentials rather than one. */
        @Test
        void theAgentCannotResetTheStudy() throws Exception {
            mvc.perform(post("/api/admin/reset/all").with(httpBasic("agent", "ag3nt")))
                    .andExpect(status().isForbidden());
        }

        @Test
        void theAgentCannotReadTheBehaviouralLog() throws Exception {
            mvc.perform(get("/api/study/log").with(httpBasic("agent", "ag3nt")))
                    .andExpect(status().isForbidden());
        }

        @Test
        void theQueueDemandsAPassword() throws Exception {
            mvc.perform(get("/api/agent/queue")).andExpect(status().isUnauthorized());
        }

        @Test
        void theAgentCanReadTheQueue() throws Exception {
            mvc.perform(get("/api/agent/queue").with(httpBasic("agent", "ag3nt")))
                    .andExpect(status().isOk());
        }

        /** So a session can be run or rescued without a second browser profile. */
        @Test
        void theResearcherCanAlsoReadTheQueue() throws Exception {
            mvc.perform(get("/api/agent/queue").with(httpBasic("researcher", "s3cret")))
                    .andExpect(status().isOk());
        }

        /**
         * The participant's way in. If this ever needs a password the study is over: a Prolific
         * participant has no account and cannot be given one.
         */
        @Test
        void theHandoverStaysOpen() throws Exception {
            mvc.perform(post("/api/handover")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(REGISTER_BODY))
                    .andExpect(status().isOk());
        }

        /** Any other participant-facing call: unauthenticated must not mean unauthorised. */
        @Test
        void theStudyApiStaysOpen() throws Exception {
            mvc.perform(get("/api/handover/no-such-token"))
                    .andExpect(status().isNotFound());
        }

        /**
         * The voice leg. A 401 here would be the manipulation failing silently - the participant
         * would meet an assistant that cannot connect, in whichever cell they were assigned.
         */
        @Test
        void theVoiceConfigStaysOpen() throws Exception {
            mvc.perform(get("/api/voice/client-config")).andExpect(status().isOk());
        }
    }

    /**
     * A deployment that set the researcher's password and forgot the agent's. The data view works,
     * the console does not, and the study still runs - which is the point: this fails at the
     * handover, not at the front door, and the warning in the log says so.
     */
    @SpringBootTest
    @Import(MockMvcAutoConfiguration.class)
    @TestPropertySource(properties = {
            "app.researcher.username=researcher",
            "app.researcher.password=s3cret",
            "app.agent.password=",
    })
    static class WithOnlyTheResearcherPasswordSet {

        @Autowired
        MockMvc mvc;

        @Test
        void researcherStillWorks() throws Exception {
            mvc.perform(get("/api/study/log").with(httpBasic("researcher", "s3cret")))
                    .andExpect(status().isOk());
        }

        /**
         * Still reachable BY THE RESEARCHER rather than closed outright. An agent credential that
         * was never set should not strand a session that is already running: whoever is at the
         * study laptop can pick the handover up with the credential they do have.
         */
        @Test
        void theResearcherCanStillReachTheQueue() throws Exception {
            mvc.perform(get("/api/agent/queue").with(httpBasic("researcher", "s3cret")))
                    .andExpect(status().isOk());
        }

        @Test
        void theQueueIsNotOpenToEveryone() throws Exception {
            mvc.perform(get("/api/agent/queue")).andExpect(status().isUnauthorized());
        }

        @Test
        void theStudyStillRuns() throws Exception {
            mvc.perform(post("/api/handover")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(REGISTER_BODY))
                    .andExpect(status().isOk());
        }
    }

    /**
     * A deployment that forgot both. Everything staff-facing must be closed, not open - and the
     * study must still run, because taking it down mid-run over a staff-page setting would be
     * worse than the problem.
     */
    @SpringBootTest
    @Import(MockMvcAutoConfiguration.class)
    @TestPropertySource(properties = {
            "app.researcher.password=",
            "app.agent.password=",
    })
    static class WithNoPasswordsSet {

        @Autowired
        MockMvc mvc;

        @Test
        void dataIsClosedRatherThanOpen() throws Exception {
            mvc.perform(get("/api/study/log")).andExpect(status().isForbidden());
        }

        @Test
        void theResetEndpointIsClosedRatherThanOpen() throws Exception {
            mvc.perform(post("/api/admin/reset/all")).andExpect(status().isForbidden());
        }

        @Test
        void theQueueIsClosedRatherThanOpen() throws Exception {
            mvc.perform(get("/api/agent/queue")).andExpect(status().isForbidden());
        }

        @Test
        void noAccountCanBeGuessedInto() throws Exception {
            mvc.perform(get("/api/study/log").with(httpBasic("researcher", "")))
                    .andExpect(status().isForbidden());
            mvc.perform(get("/api/agent/queue").with(httpBasic("agent", "")))
                    .andExpect(status().isForbidden());
        }

        @Test
        void theStudyStillRuns() throws Exception {
            mvc.perform(post("/api/handover")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(REGISTER_BODY))
                    .andExpect(status().isOk());
        }
    }

    /**
     * Two accounts cannot share a username - InMemoryUserDetailsManager keys on it, so one would
     * silently shadow the other and the agent would end up with whichever role was registered
     * last. Refusing is better than a deployment where the confederate can wipe the database
     * because two usernames happened to match.
     *
     * <p>Checked directly rather than through a context that fails to start: a boot failure is an
     * error in the report either way, so it would not distinguish this refusal from a broken test
     * database.
     */
    @Test
    void clashingUsernamesAreRefused() {
        StudySecurityConfig config = new StudySecurityConfig("same", "a", "same", "b");
        IllegalStateException thrown = assertThrows(IllegalStateException.class,
                () -> config.userDetailsService(new BCryptPasswordEncoder()));
        assertTrue(thrown.getMessage().contains("same"),
                "the message should name the username that clashed: " + thrown.getMessage());
    }

    /** The ordinary case, for contrast: two distinct usernames give two accounts. */
    @Test
    void distinctUsernamesGiveTwoAccounts() {
        StudySecurityConfig config = new StudySecurityConfig("researcher", "a", "agent", "b");
        UserDetailsService users = config.userDetailsService(new BCryptPasswordEncoder());
        assertTrue(users.loadUserByUsername("researcher").getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_RESEARCHER")));
        assertTrue(users.loadUserByUsername("agent").getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_AGENT")));
    }
}
