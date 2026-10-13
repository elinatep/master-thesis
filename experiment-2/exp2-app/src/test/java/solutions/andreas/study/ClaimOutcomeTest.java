package solutions.andreas.study;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcAutoConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import tools.jackson.databind.ObjectMapper;

/**
 * The assessment the participant reads after filing their claim.
 *
 * <p>Runs against the real {@code configuration/study/outcome.json}, not a fixture. That file is
 * the situation the whole study rests on - 1,200 claimed, 1,080 expected, 60 awarded - and the
 * figures in it have to agree with the survey, the task card and both sets of agent instructions.
 * A test against a fixture would pass while the deployed wording said something else.
 */
@SpringBootTest
@Import(MockMvcAutoConfiguration.class)
class ClaimOutcomeTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper mapper;

    @Autowired
    private StudySessionRepository sessions;

    @Autowired
    private EventRepository events;

    /**
     * Nobody reaches the assessment without having filed something. A participant told "your claim
     * has been assessed" when they filed no claim has been lied to by the study.
     */
    @Test
    void withoutAClaimThereIsNoOutcome() throws Exception {
        String participantId = openSession();

        mvc.perform(get("/api/study/outcome")
                        .header(HeaderParticipantRefProvider.HEADER, participantId))
                .andExpect(status().isConflict());
    }

    /**
     * And a browser cannot talk its way in. /api/study/events is open by necessity - Prolific
     * participants have no account - so a forged CLAIM_SUBMITTED from the console must not count.
     */
    @Test
    void aClientSideClaimEventDoesNotCount() throws Exception {
        String participantId = openSession();
        Long sessionId = sessionIdOf(participantId);

        mvc.perform(post("/api/study/events")
                        .header(HeaderParticipantRefProvider.HEADER, participantId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(new StudyController.EventBatch(
                                sessionId,
                                java.util.List.of(new StudyController.EventInput(
                                        1L, "CLAIM_SUBMITTED", Map.of(), null))))))
                .andExpect(status().is2xxSuccessful());

        mvc.perform(get("/api/study/outcome")
                        .header(HeaderParticipantRefProvider.HEADER, participantId))
                .andExpect(status().isConflict());
    }

    @Test
    void afterARealSubmissionTheAssessmentIsShown() throws Exception {
        String participantId = openSession();
        submitClaim(participantId);

        mvc.perform(get("/api/study/outcome")
                        .header(HeaderParticipantRefProvider.HEADER, participantId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scripted.panel.heading").value("Claim assessment"))
                .andExpect(jsonPath("$.scripted.escalation.contact").exists());
    }

    /**
     * The figures the entire study is built on. These appear in five places - this file, the task
     * card, the Qualtrics survey, ai-base.md and human-base.md - and they have drifted twice
     * already: the AI prompts said 1,000/50/900 and the debrief still says 900.
     */
    @Test
    void theAssessmentCarriesTheAmountsTheRestOfTheStudyUses() throws Exception {
        String participantId = openSession();
        submitClaim(participantId);

        String body = mvc.perform(get("/api/study/outcome")
                        .header(HeaderParticipantRefProvider.HEADER, participantId))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(body).contains("1,200").contains("1,080").contains("60");
        assertThat(body)
                .as("the amounts the earlier version of this study used, which must not come back")
                .doesNotContain("1,000").doesNotContain("900").doesNotContain("CHF");
    }

    /**
     * The participant's screens are identical in all four cells. Only what they hear differs, so
     * nothing on the way to the call may carry either factor.
     */
    @Test
    void theOutcomeRevealsNoCondition() throws Exception {
        String participantId = openSession();
        submitClaim(participantId);

        String body = mvc.perform(get("/api/study/outcome")
                        .header(HeaderParticipantRefProvider.HEADER, participantId))
                .andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContain("aiAck").doesNotContain("humanAck");
    }

    @Test
    void withoutASessionItIsABadRequest() throws Exception {
        mvc.perform(get("/api/study/outcome")
                        .header(HeaderParticipantRefProvider.HEADER, "p-never-" + UUID.randomUUID()))
                .andExpect(status().isBadRequest());
    }

    // ---- helpers -------------------------------------------------------------

    /**
     * Stands in for the portal's own sink, which writes this row when a claim is really created.
     * Written directly because driving the portal's claim form from here would test the library
     * rather than this endpoint.
     */
    private void submitClaim(String participantId) {
        Long sessionId = sessionIdOf(participantId);
        events.save(Event.server(sessionId, OutcomeController.CLAIM_SUBMITTED, Map.of()));
    }

    private Long sessionIdOf(String participantId) {
        return sessions.findFirstByParticipantIdOrderByStartedAtDesc(participantId)
                .orElseThrow()
                .getId();
    }

    /** A participant id unique to this run - the events below commit. */
    private String openSession() throws Exception {
        String participantId = "p-outcome-" + UUID.randomUUID().toString().substring(0, 8);
        mvc.perform(post("/api/study/session")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(new StudyController.SessionRequest(
                                participantId, true, true, "junit"))))
                .andExpect(status().isOk());
        return participantId;
    }
}
