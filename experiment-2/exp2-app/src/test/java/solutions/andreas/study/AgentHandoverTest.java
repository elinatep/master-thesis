package solutions.andreas.study;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcAutoConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

/**
 * The AI-to-human transfer. What is being protected here is the participant's experience of one
 * continuous call: they are told a colleague is coming, and exactly one colleague has to arrive,
 * holding what the assistant learned.
 */
@SpringBootTest
@Import(MockMvcAutoConfiguration.class)
class AgentHandoverTest {

    private static final String SUMMARY = """
            {"dispute_summary":"Says the 90% cover was clear when she filed.",\
            "requested_outcome":"The full 900 pounds.",\
            "grounds_given":"Policy page showed 90% for water damage.",\
            "customer_state":"very frustrated"}""";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper mapper;

    @Test
    void transferEnqueuesTheParticipantWithTheAssistantsSummary() throws Exception {
        String participantId = openSession("p-transfer", true, true);

        mvc.perform(requestAgent(participantId, SUMMARY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.handoverId", notNullValue()))
                .andExpect(jsonPath("$.status", is("waiting")))
                .andExpect(jsonPath("$.position", greaterThanOrEqualTo(1)));

        mvc.perform(get("/api/agent/queue"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.participantId=='" + participantId + "')].summary.customer_state")
                        .value("very frustrated"));
    }

    /**
     * A retried tool call or a reconnected data channel must not enqueue the participant twice. A
     * duplicate would be answered by a second agent with no memory of the first call - the exact
     * discontinuity the experiment is measuring, manufactured by a bug.
     */
    @Test
    void repeatedTransfersReturnTheSameTicket() throws Exception {
        String participantId = openSession("p-retry", false, true);

        Long first = handoverIdFrom(mvc.perform(requestAgent(participantId, SUMMARY))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        Long second = handoverIdFrom(mvc.perform(requestAgent(participantId, SUMMARY))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());

        org.assertj.core.api.Assertions.assertThat(second).isEqualTo(first);
    }

    /**
     * The queue carries the human-acknowledgement factor, joined from the session - it is what the
     * agent's condition card is built from. It must be the cell the participant was assigned.
     */
    @Test
    void queueCarriesTheHumanAcknowledgementCellForTheAgent() throws Exception {
        String noAck = openSession("p-cell-noack", true, false);
        mvc.perform(requestAgent(noAck, SUMMARY)).andExpect(status().isOk());

        mvc.perform(get("/api/agent/queue"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.participantId=='" + noAck + "')].humanAck").value(false));
    }

    /** A malformed summary must not strand a participant who has been told a human is coming. */
    @Test
    void malformedSummaryStillTransfers() throws Exception {
        String participantId = openSession("p-badjson", true, true);

        mvc.perform(requestAgent(participantId, "not json at all"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("waiting")));
    }

    @Test
    void transferWithoutASessionIsRejected() throws Exception {
        mvc.perform(requestAgent("p-never-started", SUMMARY))
                .andExpect(status().isBadRequest());
    }

    // ---- helpers -------------------------------------------------------------

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder requestAgent(
            String participantId, String summary) throws Exception {
        return post("/api/study/agent-handover")
                .header(HeaderParticipantRefProvider.HEADER, participantId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(
                        new AgentHandoverController.HandoverRequest(summary)));
    }

    private String openSession(String participantId, boolean aiAck, boolean humanAck) throws Exception {
        mvc.perform(post("/api/study/session")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(new StudyController.SessionRequest(
                                participantId, aiAck, humanAck, "junit"))))
                .andExpect(status().isOk());
        return participantId;
    }

    private Long handoverIdFrom(String json) {
        return mapper.readTree(json).get("handoverId").asLong();
    }
}
