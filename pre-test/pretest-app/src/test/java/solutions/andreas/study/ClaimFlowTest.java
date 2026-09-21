package solutions.andreas.study;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.assertj.core.api.Assertions;
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
 * The participant files their claim through the portal's own endpoint. The study sits in front of
 * it and decides, per arm, whether that submission is allowed to succeed.
 *
 * <p>These run against the real {@code configuration/arms/} files rather than fixtures - they exist
 * to catch an edit to the arms as much as a regression in the code.
 */
@SpringBootTest
@Import(MockMvcAutoConfiguration.class)
class ClaimFlowTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper mapper;

    /** S0, S1 and S2 let the claim through: a real claim is created by the portal. */
    @ParameterizedTest
    @ValueSource(strings = { "S0", "S1", "S2" })
    void succeedingArmsLetThePortalFileTheClaim(String arm) throws Exception {
        String participantId = startParticipant("claim-" + arm, arm);

        mvc.perform(fileClaim(participantId))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.claimNumber", containsString("CLM-")))
                .andExpect(jsonPath("$.status", is("SUBMITTED")));

        mvc.perform(outcome(participantId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.arm", is(arm)))
                .andExpect(jsonPath("$.finalAttempt", is(true)))
                .andExpect(jsonPath("$.scripted.result", is("SUBMITTED")));
    }

    @Test
    void controlArmApprovesTheClaimInFull() throws Exception {
        String participantId = startParticipant("claim-control", "S0");
        mvc.perform(fileClaim(participantId)).andExpect(status().isCreated());

        mvc.perform(outcome(participantId))
                .andExpect(jsonPath("$.scripted.panel.body[0]", containsString("approved as expected")));
    }

    /** The anger arm's defining feature: a large shortfall with no reason offered. */
    @Test
    void angerArmWithholdsAnExplanation() throws Exception {
        String participantId = startParticipant("claim-anger", "S1");
        mvc.perform(fileClaim(participantId)).andExpect(status().isCreated());

        mvc.perform(outcome(participantId))
                .andExpect(jsonPath("$.scripted.panel.body[0]",
                        containsString("No further explanation is provided")));
    }

    /** The worry arm's defining feature: not resolved, and no timeline for resolution. */
    @Test
    void worryArmLeavesTheOutcomeOpen() throws Exception {
        String participantId = startParticipant("claim-worry", "S2");
        mvc.perform(fileClaim(participantId)).andExpect(status().isCreated());

        mvc.perform(outcome(participantId))
                .andExpect(jsonPath("$.scripted.panel.heading", is("Provisional claim assessment")))
                .andExpect(jsonPath("$.scripted.panel.body[0]",
                        containsString("no timeline for the final decision")));
    }

    /**
     * The irritation arm has to fail exactly twice, and the failure must reach the participant in
     * the portal's own error alert with the arm's wording. The number of failures IS the
     * manipulation, so this is the test that matters most in the file.
     */
    @Test
    void irritationArmFailsTwiceThenStops() throws Exception {
        String participantId = startParticipant("claim-irritation", "S4");

        mvc.perform(fileClaim(participantId))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message", containsString("E-4092")));

        mvc.perform(outcome(participantId))
                .andExpect(jsonPath("$.attemptNumber", is(1)))
                .andExpect(jsonPath("$.totalAttempts", is(2)))
                .andExpect(jsonPath("$.finalAttempt", is(false)))
                .andExpect(jsonPath("$.scripted.retry.notice", is("Attempt 2 of 2")));

        mvc.perform(fileClaim(participantId))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message", containsString("occurred again")));

        mvc.perform(outcome(participantId))
                .andExpect(jsonPath("$.attemptNumber", is(2)))
                .andExpect(jsonPath("$.finalAttempt", is(true)))
                .andExpect(jsonPath("$.scripted.panel.body[0]",
                        containsString("unable to complete your claim online")));
    }

    /** No claim may survive a scripted failure: "the information you entered was not saved". */
    @Test
    void aFailedSubmissionCreatesNoClaim() throws Exception {
        String participantId = startParticipant("claim-nosave", "S4");
        mvc.perform(fileClaim(participantId)).andExpect(status().isInternalServerError());

        String claims = mvc.perform(get("/api/claims")
                        .header(HeaderParticipantRefProvider.HEADER, participantId))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        Assertions.assertThat(claims).isEqualTo("[]");
    }

    /**
     * A reload mid-arm must not restart the script. The attempt count is derived from the logged
     * attempts rather than held in the browser, so asking twice gives the same answer.
     */
    @Test
    void reloadingTheOutcomeShowsTheSameStep() throws Exception {
        String participantId = startParticipant("claim-reload", "S4");
        mvc.perform(fileClaim(participantId)).andExpect(status().isInternalServerError());

        for (int i = 0; i < 2; i++) {
            mvc.perform(outcome(participantId))
                    .andExpect(jsonPath("$.attemptNumber", is(1)))
                    .andExpect(jsonPath("$.finalAttempt", is(false)));
        }
    }

    /**
     * Every attempt is logged with what was submitted, including the ones that never reached the
     * portal. How the re-entered claim differs from the first is part of what the pre-test looks at,
     * and the portal cannot report a submission it was never given.
     */
    @Test
    void everyAttemptIsLoggedWithWhatWasSubmitted() throws Exception {
        String participantId = startParticipant("claim-logging", "S4");

        mvc.perform(fileClaim(participantId, "WATER_DAMAGE", "Burst pipe in the kitchen", "1200"));
        mvc.perform(fileClaim(participantId, "WATER_DAMAGE", "pipe burst, kitchen and hallway", "1200"));

        String log = mvc.perform(get("/api/study/log"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        Assertions.assertThat(log).contains("Burst pipe in the kitchen");
        Assertions.assertThat(log).contains("pipe burst, kitchen and hallway");
    }

    /** Asking for an outcome before filing anything is a conflict, not an empty success. */
    @Test
    void outcomeBeforeAnyClaimIsRejected() throws Exception {
        String participantId = startParticipant("claim-early", "S1");

        mvc.perform(outcome(participantId)).andExpect(status().isConflict());
    }

    /**
     * The filter must not take over requests that are not part of a participant run - a researcher
     * poking the API has no session, and the portal should answer for itself.
     */
    @Test
    void requestsWithoutASessionFallThroughToThePortal() throws Exception {
        mvc.perform(post("/api/policies/1/claims")
                        .header(HeaderParticipantRefProvider.HEADER, "no-session-here")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"WATER_DAMAGE\",\"incidentDate\":\"2026-09-20\",\"amount\":1200}"))
                .andExpect(status().is4xxClientError());
    }

    // ---- helpers -------------------------------------------------------------

    private MockHttpServletRequestBuilder outcome(String participantId) {
        return get("/api/pretest/outcome").header(HeaderParticipantRefProvider.HEADER, participantId);
    }

    private MockHttpServletRequestBuilder fileClaim(String participantId) throws Exception {
        return fileClaim(participantId, "WATER_DAMAGE", "Burst water pipe in the kitchen", "1200");
    }

    private MockHttpServletRequestBuilder fileClaim(String participantId, String type,
            String description, String amount) throws Exception {
        long policyId = householdPolicyId(participantId);
        return post("/api/policies/{id}/claims", policyId)
                .header(HeaderParticipantRefProvider.HEADER, participantId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"type":"%s","description":"%s","incidentDate":"2026-09-20","amount":%s}"""
                        .formatted(type, description, amount));
    }

    /** The policy the scenario points at: the participant's household contents cover. */
    private long householdPolicyId(String participantId) throws Exception {
        String body = mvc.perform(get("/api/policies")
                        .header(HeaderParticipantRefProvider.HEADER, participantId))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        for (var policy : mapper.readTree(body)) {
            if ("HOUSEHOLD".equals(policy.get("type").asString())) {
                return policy.get("id").asLong();
            }
        }
        throw new AssertionError("No household policy was seeded for " + participantId);
    }

    /** Open the behavioural session and provision the portal account, as the gate does on entry. */
    private String startParticipant(String participantId, String arm) throws Exception {
        mvc.perform(post("/api/study/session")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(
                                new StudyController.SessionRequest(participantId, arm, "junit"))))
                .andExpect(status().isOk());
        mvc.perform(post("/api/study/intake")
                        .header(HeaderParticipantRefProvider.HEADER, participantId))
                .andExpect(status().is2xxSuccessful());
        return participantId;
    }
}
