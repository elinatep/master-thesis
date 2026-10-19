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
 * What happens when a participant presses reload.
 *
 * <p>It is not an edge case. The study runs for ten minutes or so on the participant's own machine,
 * through a claim form, an assessment and a voice call, and some of them will refresh - because a
 * page looked stuck, because the microphone prompt was missed, because that is what people do.
 *
 * <p>The gate opens a session on every page load, so a reload used to start a SECOND one. Everything
 * downstream reads "the participant's most recent session", so that empty second session became
 * their current one: the claim they had filed no longer counted, the assessment told them no claim
 * had been filed, and an open handover could not be rejoined. The participant was quietly finished
 * and nothing said so.
 */
@SpringBootTest
@Import(MockMvcAutoConfiguration.class)
class SessionReloadTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper mapper;

    @Autowired
    private StudySessionRepository sessions;

    @Autowired
    private EventRepository events;

    @Test
    void reloadingKeepsTheSameSession() throws Exception {
        String participantId = participant();

        long first = openSession(participantId, true, true);
        long second = openSession(participantId, true, true);

        assertThat(second).isEqualTo(first);
    }

    /** The point of the above: a reload must not lose the claim they already filed. */
    @Test
    void theAssessmentSurvivesAReload() throws Exception {
        String participantId = participant();
        long sessionId = openSession(participantId, true, true);
        events.save(Event.server(sessionId, OutcomeController.CLAIM_SUBMITTED, Map.of()));

        openSession(participantId, true, true);

        mvc.perform(get("/api/study/outcome")
                        .header(HeaderParticipantRefProvider.HEADER, participantId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scripted.panel.heading").value("Claim assessment"));
    }

    /** And must not lose their place with the agent. */
    @Test
    void anOpenHandoverSurvivesAReload() throws Exception {
        String participantId = participant();
        openSession(participantId, true, true);
        mvc.perform(post("/api/study/agent-handover")
                        .header(HeaderParticipantRefProvider.HEADER, participantId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(
                                new AgentHandoverController.HandoverRequest("{}"))))
                .andExpect(status().isOk());

        openSession(participantId, true, true);

        mvc.perform(get("/api/study/agent-handover")
                        .header(HeaderParticipantRefProvider.HEADER, participantId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("waiting"))
                .andExpect(jsonPath("$.callId").exists());
    }

    /**
     * A different condition is a different assignment, not a reload. Reusing the session would put
     * the participant in a cell Qualtrics did not give them, with the behavioural log saying the
     * old one - which is the corruption that is invisible once the run is over.
     */
    @Test
    void aDifferentConditionStartsAFreshSession() throws Exception {
        String participantId = participant();

        long first = openSession(participantId, true, true);
        long second = openSession(participantId, false, true);

        assertThat(second).isNotEqualTo(first);
    }

    /** Once they have finished, a new entry is a new run. */
    @Test
    void afterCompletionANewEntryOpensANewSession() throws Exception {
        String participantId = participant();
        long first = openSession(participantId, true, true);

        mvc.perform(post("/api/study/session/" + first + "/complete")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().is2xxSuccessful());

        assertThat(openSession(participantId, true, true)).isNotEqualTo(first);
    }

    /** The reused session keeps its original start time - it is one run, not two. */
    @Test
    void theReusedSessionIsTheOriginalRow() throws Exception {
        String participantId = participant();
        long first = openSession(participantId, true, true);
        var startedAt = sessions.findById(first).orElseThrow().getStartedAt();

        openSession(participantId, true, true);

        assertThat(sessions.findById(first).orElseThrow().getStartedAt()).isEqualTo(startedAt);
    }

    // ---- helpers -------------------------------------------------------------

    private long openSession(String participantId, boolean aiAck, boolean humanAck) throws Exception {
        String body = mvc.perform(post("/api/study/session")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(new StudyController.SessionRequest(
                                participantId, aiAck, humanAck, "junit"))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return mapper.readTree(body).get("sessionId").asLong();
    }

    private static String participant() {
        return "p-reload-" + UUID.randomUUID().toString().substring(0, 8);
    }
}
