package solutions.andreas.study;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcAutoConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.ObjectMapper;

/**
 * The claim flow is the manipulation, so these tests run against the real
 * {@code configuration/arms/} files rather than fixtures - they exist to catch an edit to the arms
 * as much as a regression in the code.
 */
@SpringBootTest
@Import(MockMvcAutoConfiguration.class)
class ClaimFlowTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper mapper;

    /** The three non-failing arms settle on the first submission and differ only in the outcome. */
    @ParameterizedTest
    @ValueSource(strings = { "S0", "S1", "S2" })
    void singleAttemptArmsSubmitSuccessfullyFirstTime(String arm) throws Exception {
        String participantId = openSession("claim-" + arm, arm);

        mvc.perform(submitClaim(participantId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.arm", is(arm)))
                .andExpect(jsonPath("$.attemptNumber", is(1)))
                .andExpect(jsonPath("$.totalAttempts", is(1)))
                .andExpect(jsonPath("$.finalAttempt", is(true)))
                .andExpect(jsonPath("$.scripted.result", is("SUBMITTED")));
    }

    @Test
    void controlArmApprovesTheClaimInFull() throws Exception {
        String participantId = openSession("claim-control", "S0");

        mvc.perform(submitClaim(participantId))
                .andExpect(jsonPath("$.scripted.panel.body[0]",
                        containsString("approved as expected")));
    }

    /** The anger arm's defining feature: a large shortfall with no reason offered. */
    @Test
    void angerArmWithholdsAnExplanation() throws Exception {
        String participantId = openSession("claim-anger", "S1");

        mvc.perform(submitClaim(participantId))
                .andExpect(jsonPath("$.scripted.panel.body[0]",
                        containsString("No further explanation is provided")));
    }

    /** The worry arm's defining feature: not resolved, and no timeline for resolution. */
    @Test
    void worryArmLeavesTheOutcomeOpen() throws Exception {
        String participantId = openSession("claim-worry", "S2");

        mvc.perform(submitClaim(participantId))
                .andExpect(jsonPath("$.scripted.panel.heading", is("Provisional claim assessment")))
                .andExpect(jsonPath("$.scripted.panel.body[0]",
                        containsString("no timeline for the final decision")));
    }

    /**
     * The irritation arm has to fail exactly twice: once with a retry, once terminally. The number
     * of failures IS the manipulation, so this is the test that matters most in the file.
     */
    @Test
    void irritationArmFailsTwiceThenStops() throws Exception {
        String participantId = openSession("claim-irritation", "S4");

        mvc.perform(submitClaim(participantId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.attemptNumber", is(1)))
                .andExpect(jsonPath("$.totalAttempts", is(2)))
                .andExpect(jsonPath("$.finalAttempt", is(false)))
                .andExpect(jsonPath("$.scripted.result", is("ERROR")))
                .andExpect(jsonPath("$.scripted.retry.notice", is("Attempt 2 of 2")));

        mvc.perform(submitClaim(participantId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.attemptNumber", is(2)))
                .andExpect(jsonPath("$.finalAttempt", is(true)))
                .andExpect(jsonPath("$.scripted.result", is("ERROR")))
                .andExpect(jsonPath("$.scripted.panel.body[0]",
                        containsString("unable to complete your claim online")));
    }

    /**
     * A reload mid-S4 must not restart the script. The attempt count is derived from the logged
     * attempts rather than held in the browser, so the participant comes back to attempt 2 - and
     * gets one more failure, not another two.
     */
    @Test
    void reloadingMidFailureResumesRatherThanRestarting() throws Exception {
        String participantId = openSession("claim-reload", "S4");

        mvc.perform(submitClaim(participantId)).andExpect(jsonPath("$.attemptNumber", is(1)));

        // What the browser asks for on re-entry.
        mvc.perform(get("/api/pretest/claim")
                        .header(HeaderParticipantRefProvider.HEADER, participantId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.attemptNumber", is(2)))
                .andExpect(jsonPath("$.previousOutcome.result", is("ERROR")));

        mvc.perform(submitClaim(participantId)).andExpect(jsonPath("$.finalAttempt", is(true)));
    }

    /**
     * What the participant typed is logged before the outcome is decided, including on the forced
     * retry. How the re-entered claim differs from the first is part of what the pre-test looks at.
     */
    @Test
    void everyAttemptIsLoggedWithWhatWasTyped() throws Exception {
        String participantId = openSession("claim-logging", "S4");

        mvc.perform(submitClaim(participantId, "Water damage", "Burst water pipe", "2026-09-20", "1200"));
        mvc.perform(submitClaim(participantId, "Water damage", "burst pipe in kitchen", "2026-09-20", "1,200"));

        String log = mvc.perform(get("/api/study/log"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(log).contains("burst pipe in kitchen");
        assertThat(log).contains("Burst water pipe");
    }

    @Test
    void anEmptyClaimIsRejected() throws Exception {
        String participantId = openSession("claim-empty", "S0");

        mvc.perform(submitClaim(participantId, "", "", "", ""))
                .andExpect(status().isBadRequest());
    }

    @Test
    void claimWithoutASessionIsRejected() throws Exception {
        mvc.perform(submitClaim("never-started"))
                .andExpect(status().isBadRequest());
    }

    // ---- helpers -------------------------------------------------------------

    private MockHttpServletRequestBuilder submitClaim(String participantId) throws Exception {
        return submitClaim(participantId, "Water damage", "Burst water pipe", "2026-09-20", "1200");
    }

    private MockHttpServletRequestBuilder submitClaim(String participantId, String type, String cause,
            String date, String amount) throws Exception {
        return post("/api/pretest/claim")
                .header(HeaderParticipantRefProvider.HEADER, participantId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(
                        new PretestClaimController.ClaimSubmission(type, cause, date, amount)));
    }

    private String openSession(String participantId, String arm) throws Exception {
        mvc.perform(post("/api/study/session")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(
                                new StudyController.SessionRequest(participantId, arm, "junit"))))
                .andExpect(status().isOk());
        return participantId;
    }
}
