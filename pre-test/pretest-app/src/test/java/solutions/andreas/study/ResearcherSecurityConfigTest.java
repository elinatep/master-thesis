package solutions.andreas.study;

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
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * What this guards is a pair of opposite mistakes, both of which are invisible in a browser that
 * happens to be logged in: locking the participants out of the study, and leaving their data open.
 *
 * <p>The second is the reason the config exists. The first is the reason it is easy to get wrong -
 * adding Spring Security to an application secures everything by default, so every participant-
 * facing path has to be permitted explicitly, and a later edit that drops one would break the
 * study for real participants while every test that only checks /data still passes.
 */
class ResearcherSecurityConfigTest {

    /** A deployment with a password set: the researcher gets in, nobody else does. */
    @SpringBootTest
    @Import(MockMvcAutoConfiguration.class)
    @TestPropertySource(properties = {
            "app.researcher.username=researcher",
            "app.researcher.password=s3cret",
    })
    static class WithPasswordSet {

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
        void rightPasswordGetsIn() throws Exception {
            mvc.perform(get("/api/study/log").with(httpBasic("researcher", "s3cret")))
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
                            .content("{\"participantId\":\"p1\",\"arm\":\"S1\"}"))
                    .andExpect(status().isOk());
        }

        /** Any other participant-facing call: unauthenticated must not mean unauthorised. */
        @Test
        void theStudyApiStaysOpen() throws Exception {
            mvc.perform(get("/api/handover/no-such-token"))
                    .andExpect(status().isNotFound());
        }
    }

    /**
     * A deployment that forgot the password. The data must be closed, not open - and the study must
     * still run, because taking it down mid-run over a researcher-page setting would be worse than
     * the problem.
     */
    @SpringBootTest
    @Import(MockMvcAutoConfiguration.class)
    @TestPropertySource(properties = "app.researcher.password=")
    static class WithNoPasswordSet {

        @Autowired
        MockMvc mvc;

        @Test
        void dataIsClosedRatherThanOpen() throws Exception {
            mvc.perform(get("/api/study/log")).andExpect(status().isForbidden());
        }

        @Test
        void noAccountCanBeGuessedInto() throws Exception {
            mvc.perform(get("/api/study/log").with(httpBasic("researcher", "")))
                    .andExpect(status().isForbidden());
        }

        @Test
        void theStudyStillRuns() throws Exception {
            mvc.perform(post("/api/handover")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"participantId\":\"p1\",\"arm\":\"S1\"}"))
                    .andExpect(status().isOk());
        }
    }
}
