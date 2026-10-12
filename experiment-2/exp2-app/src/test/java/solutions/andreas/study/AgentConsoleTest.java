package solutions.andreas.study;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcAutoConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import tools.jackson.databind.ObjectMapper;

/**
 * The agent console: claiming a waiting participant, and what the agent is and is not told.
 *
 * <p>Calling itself is not exercised here - that needs a real Azure Communication Services
 * resource. What is exercised is everything around it: who may claim a handover, what happens when
 * two agents try, and the shape of the brief.
 */
@SpringBootTest
@Import(MockMvcAutoConfiguration.class)
@TestPropertySource(properties = {
        "app.agent.username=agent",
        "app.agent.password=ag3nt",
        "app.researcher.username=researcher",
        "app.researcher.password=s3cret",
        // No ACS in a test. The endpoints that need it must say so clearly rather than fail oddly.
        "app.call.connection-string=",
})
class AgentConsoleTest {

    private static final String SUMMARY = """
            {"dispute_summary":"Says the 90% cover was clear when she filed.",\
            "requested_outcome":"The full 1,080 pounds.",\
            "grounds_given":"Policy page showed 90% for water damage.",\
            "customer_state":"very frustrated"}""";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper mapper;

    @Test
    void pickingUpClaimsTheHandoverAndNamesTheAgent() throws Exception {
        Long handoverId = enqueue(true);

        mvc.perform(post("/api/agent/handover/" + handoverId + "/pickup").with(agent()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.agentId").value("agent"))
                .andExpect(jsonPath("$.pickedUpAt").exists())
                .andExpect(jsonPath("$.humanAck").value(true));
    }

    /**
     * Two agents, one participant. The second must be refused: two people picking up the same
     * participant is the discontinuity this experiment measures, manufactured by a race.
     */
    @Test
    void aSecondPickupIsRefused() throws Exception {
        Long handoverId = enqueue(false);

        mvc.perform(post("/api/agent/handover/" + handoverId + "/pickup").with(agent()))
                .andExpect(status().isOk());
        mvc.perform(post("/api/agent/handover/" + handoverId + "/pickup").with(agent()))
                .andExpect(status().isConflict());
    }

    /** Once claimed, they are no longer waiting - or a second console would still offer them. */
    @Test
    void aPickedUpHandoverLeavesTheQueue() throws Exception {
        Long handoverId = enqueue(true);

        mvc.perform(post("/api/agent/handover/" + handoverId + "/pickup").with(agent()))
                .andExpect(status().isOk());

        String queue = mvc.perform(get("/api/agent/queue").with(agent()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(queue).doesNotContain("\"handoverId\":" + handoverId + ",");
    }

    /**
     * The decision this test exists to hold: the agent is told their own instruction and nothing
     * about the AI leg.
     *
     * <p>They can read the transcript and will often infer it, so this is not blinding - it is
     * consistency. Printing the factor on the card would make an expectancy effect uniform across
     * every session instead of varying with how closely the agent read. A later "convenience" field
     * would quietly undo that, and nothing else in the codebase would notice.
     */
    @Test
    void theBriefNeverRevealsTheAiCondition() throws Exception {
        Long handoverId = enqueue(true);

        for (MockHttpServletRequestBuilder request : java.util.List.of(
                post("/api/agent/handover/" + handoverId + "/pickup").with(agent()),
                get("/api/agent/handover/" + handoverId).with(agent()),
                get("/api/agent/queue").with(agent()))) {
            String body = mvc.perform(request).andReturn().getResponse().getContentAsString();
            assertThat(body)
                    .as("no agent-facing payload may carry the AI factor")
                    .doesNotContain("aiAck")
                    .doesNotContain("\"cell\"");
        }
    }

    /** The brief carries the AI leg, because the agent has to open already knowing it. */
    @Test
    void theBriefCarriesTheTranscriptAndTheSummary() throws Exception {
        Long handoverId = enqueue(true, "I was told ninety per cent and I got sixty back.");

        mvc.perform(get("/api/agent/handover/" + handoverId).with(agent()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transcript[0].text")
                        .value("I was told ninety per cent and I got sixty back."))
                .andExpect(jsonPath("$.summary.customer_state").value("very frustrated"))
                .andExpect(jsonPath("$.callId").exists());
    }

    /** A reloaded console mid-call has to get back to the same screen without re-claiming. */
    @Test
    void theBriefCanBeReadWithoutClaiming() throws Exception {
        Long handoverId = enqueue(true);

        mvc.perform(get("/api/agent/handover/" + handoverId).with(agent()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pickedUpAt").doesNotExist());
    }

    @Test
    void endingIsIdempotent() throws Exception {
        Long handoverId = enqueue(true);

        mvc.perform(post("/api/agent/handover/" + handoverId + "/pickup").with(agent()))
                .andExpect(status().isOk());
        mvc.perform(post("/api/agent/handover/" + handoverId + "/end").with(agent()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.endedAt").exists());
        // Hanging up and then clicking "end" must not be an error to think about mid-call.
        mvc.perform(post("/api/agent/handover/" + handoverId + "/end").with(agent()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.endedAt").exists());
    }

    @Test
    void anUnknownHandoverIsNotFound() throws Exception {
        mvc.perform(get("/api/agent/handover/99999999").with(agent()))
                .andExpect(status().isNotFound());
    }

    /**
     * Without ACS configured, both sides must be told which setting is missing. A blank token would
     * fail inside the Calling SDK as a credential error, several steps from the cause - during a
     * session, with a participant waiting.
     */
    @Test
    void withoutCallingConfiguredTheAgentIsToldSo() throws Exception {
        Long handoverId = enqueue(true);

        mvc.perform(post("/api/agent/handover/" + handoverId + "/pickup").with(agent()))
                .andExpect(status().isOk());
        mvc.perform(post("/api/agent/handover/" + handoverId + "/call-token").with(agent()))
                .andExpect(status().isServiceUnavailable());
    }

    @Test
    void theParticipantIsAlsoToldSo() throws Exception {
        String participantId = openSession("p-cfg", true, true);
        mvc.perform(requestAgent(participantId)).andExpect(status().isOk());

        mvc.perform(post("/api/call/token")
                        .header(HeaderParticipantRefProvider.HEADER, participantId))
                .andExpect(status().isServiceUnavailable());
    }

    /**
     * No handover, no call. Otherwise anyone who found this host could mint ACS credentials, and
     * this endpoint has to stay open - a Prolific participant has no account.
     */
    @Test
    void aParticipantWithNoHandoverGetsNoCall() throws Exception {
        String participantId = openSession("p-nocall", true, true);

        mvc.perform(post("/api/call/token")
                        .header(HeaderParticipantRefProvider.HEADER, participantId))
                .andExpect(status().isConflict());
    }

    /** What the waiting participant's page polls, so it can stop being silent at them. */
    @Test
    void theParticipantCanSeeWhereTheirTransferHasGot() throws Exception {
        String participantId = openSession("p-status", false, true);
        mvc.perform(requestAgent(participantId)).andExpect(status().isOk());

        String waiting = mvc.perform(get("/api/study/agent-handover")
                        .header(HeaderParticipantRefProvider.HEADER, participantId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("waiting"))
                .andExpect(jsonPath("$.callId").exists())
                .andReturn().getResponse().getContentAsString();
        // The participant's own page must not learn the condition either - in either factor.
        assertThat(waiting).doesNotContain("humanAck").doesNotContain("aiAck");

        Long handoverId = mapper.readTree(waiting).get("handoverId").asLong();
        mvc.perform(post("/api/agent/handover/" + handoverId + "/pickup").with(agent()))
                .andExpect(status().isOk());

        mvc.perform(get("/api/study/agent-handover")
                        .header(HeaderParticipantRefProvider.HEADER, participantId))
                .andExpect(jsonPath("$.state").value("connected"));
    }

    /** The researcher is let into the console too, so a session can be rescued. */
    @Test
    void theResearcherCanAlsoWorkTheQueue() throws Exception {
        enqueue(true);

        mvc.perform(get("/api/agent/queue").with(httpBasic("researcher", "s3cret")))
                .andExpect(status().isOk());
    }

    // ---- helpers -------------------------------------------------------------

    private static org.springframework.test.web.servlet.request.RequestPostProcessor agent() {
        return httpBasic("agent", "ag3nt");
    }

    private Long enqueue(boolean humanAck) throws Exception {
        return enqueue(humanAck, null);
    }

    /** Opens a session, optionally logs one transcript line, and transfers. */
    private Long enqueue(boolean humanAck, String saidToTheAssistant) throws Exception {
        String participantId = openSession("p-console", true, humanAck);

        if (saidToTheAssistant != null) {
            Long sessionId = sessionIdOf(participantId);
            mvc.perform(post("/api/study/transcript")
                            .header(HeaderParticipantRefProvider.HEADER, participantId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(mapper.writeValueAsString(new StudyController.TranscriptBatch(
                                    sessionId,
                                    java.util.List.of(new StudyController.TranscriptInput(
                                            1L, "user", saidToTheAssistant, null))))))
                    // 204: the endpoint takes a batch and returns nothing.
                    .andExpect(status().isNoContent());
        }

        String ticket = mvc.perform(requestAgent(participantId))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return mapper.readTree(ticket).get("handoverId").asLong();
    }

    private Long sessionIdOf(String participantId) throws Exception {
        String status = mvc.perform(post("/api/study/session")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(new StudyController.SessionRequest(
                                participantId, true, true, "junit"))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return mapper.readTree(status).get("sessionId").asLong();
    }

    private MockHttpServletRequestBuilder requestAgent(String participantId) throws Exception {
        return post("/api/study/agent-handover")
                .header(HeaderParticipantRefProvider.HEADER, participantId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(
                        new AgentHandoverController.HandoverRequest(SUMMARY)));
    }

    /**
     * A participant id unique to this run - the transfer commits, so a fixed id would accumulate a
     * row per `mvn test` and the queue assertions would start failing on the second run.
     */
    private String openSession(String prefix, boolean aiAck, boolean humanAck) throws Exception {
        String participantId = prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
        mvc.perform(post("/api/study/session")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(new StudyController.SessionRequest(
                                participantId, aiAck, humanAck, "junit"))))
                .andExpect(status().isOk());
        return participantId;
    }
}
