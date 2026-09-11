package solutions.andreas.study;

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcAutoConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

/**
 * The Qualtrics handover carries the assigned cell of the 2x2, and it is the only thing that does.
 * If a factor is dropped or flipped between Qualtrics and the portal, the participant runs in the
 * wrong condition and nothing downstream can tell: the behavioural log will faithfully record
 * whatever the portal was told. So the round trip is tested in all four cells, not one.
 */
@SpringBootTest
@Import(MockMvcAutoConfiguration.class)
class ConditionIntakeTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper mapper;

    static Stream<Arguments> cells() {
        return Stream.of(
                Arguments.of(false, false),
                Arguments.of(true, false),
                Arguments.of(false, true),
                Arguments.of(true, true));
    }

    @ParameterizedTest(name = "aiAck={0} humanAck={1}")
    @MethodSource("cells")
    void registerThenExchangeReturnsTheSameCell(boolean aiAck, boolean humanAck) throws Exception {
        String participantId = "p-" + aiAck + "-" + humanAck;
        String token = register(participantId, aiAck, humanAck);

        mvc.perform(get("/api/handover/{token}", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.participantId", is(participantId)))
                .andExpect(jsonPath("$.aiAck", is(aiAck)))
                .andExpect(jsonPath("$.humanAck", is(humanAck)));
    }

    /** Reusable within its TTL: a browser reload must re-exchange to the same cell, not fail. */
    @Test
    void tokenCanBeExchangedMoreThanOnce() throws Exception {
        String token = register("p-reload", true, false);

        for (int i = 0; i < 2; i++) {
            mvc.perform(get("/api/handover/{token}", token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.aiAck", is(true)))
                    .andExpect(jsonPath("$.humanAck", is(false)));
        }
    }

    @Test
    void unknownTokenIsNotFound() throws Exception {
        mvc.perform(get("/api/handover/{token}", "no-such-token"))
                .andExpect(status().isNotFound());
    }

    /**
     * A missing factor must be a 400, never a silent false. Defaulting would put the participant in
     * a real cell of the design that nobody assigned them to - the single most damaging thing this
     * endpoint could do, and the least visible.
     */
    @Test
    void missingAiAckIsRejected() throws Exception {
        mvc.perform(post("/api/handover")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"participantId\":\"p-x\",\"humanAck\":true}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void missingHumanAckIsRejected() throws Exception {
        mvc.perform(post("/api/handover")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"participantId\":\"p-y\",\"aiAck\":true}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void blankParticipantIdIsRejected() throws Exception {
        mvc.perform(post("/api/handover")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"participantId\":\"  \",\"aiAck\":true,\"humanAck\":true}"))
                .andExpect(status().isBadRequest());
    }

    /** The cell has to survive the second hop too: intake -> the session every event hangs off. */
    @ParameterizedTest(name = "aiAck={0} humanAck={1}")
    @MethodSource("cells")
    void openedSessionCarriesTheCell(boolean aiAck, boolean humanAck) throws Exception {
        mvc.perform(post("/api/study/session")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(new StudyController.SessionRequest(
                                "s-" + aiAck + "-" + humanAck, aiAck, humanAck, "junit"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.aiAck", is(aiAck)))
                .andExpect(jsonPath("$.humanAck", is(humanAck)));
    }

    private String register(String participantId, boolean aiAck, boolean humanAck) throws Exception {
        String body = mvc.perform(post("/api/handover")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(new HandoverController.RegisterRequest(
                                participantId, aiAck, humanAck, "Alex Morgan", "https://qualtrics.example/return"))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return mapper.readTree(body).get("token").asString();
    }
}
