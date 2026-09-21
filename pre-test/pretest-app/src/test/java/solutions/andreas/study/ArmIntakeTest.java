package solutions.andreas.study;

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
import tools.jackson.databind.ObjectMapper;

/**
 * The Qualtrics handover carries the assigned arm, and it is the only thing that does. If the arm
 * is dropped or mistyped between Qualtrics and the portal, the participant sees the wrong claim
 * outcome and nothing downstream can tell - the log faithfully records whatever it was told.
 */
@SpringBootTest
@Import(MockMvcAutoConfiguration.class)
class ArmIntakeTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper mapper;

    @ParameterizedTest
    @ValueSource(strings = { "S0", "S1", "S2", "S4" })
    void registerThenExchangeReturnsTheSameArm(String arm) throws Exception {
        String participantId = "p-" + arm;
        String token = register(participantId, arm);

        mvc.perform(get("/api/handover/{token}", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.participantId", is(participantId)))
                .andExpect(jsonPath("$.arm", is(arm)));
    }

    /**
     * An arm with no file is rejected at registration. This is the guard against a renamed
     * Qualtrics randomiser element quietly opening a fifth condition that has no wording, no
     * script, and no place in the analysis.
     */
    @Test
    void unknownArmIsRejected() throws Exception {
        mvc.perform(post("/api/handover")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"participantId\":\"p-x\",\"arm\":\"S3\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void missingArmIsRejected() throws Exception {
        mvc.perform(post("/api/handover")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"participantId\":\"p-y\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void unknownTokenIsNotFound() throws Exception {
        mvc.perform(get("/api/handover/{token}", "no-such-token"))
                .andExpect(status().isNotFound());
    }

    /** Reusable within its TTL: a reload must re-exchange to the same arm, not fail. */
    @Test
    void tokenCanBeExchangedMoreThanOnce() throws Exception {
        String token = register("p-reload", "S1");
        for (int i = 0; i < 2; i++) {
            mvc.perform(get("/api/handover/{token}", token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.arm", is("S1")));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = { "S0", "S1", "S2", "S4" })
    void openedSessionCarriesTheArm(String arm) throws Exception {
        mvc.perform(post("/api/study/session")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(
                                new StudyController.SessionRequest("s-" + arm, arm, "junit"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.arm", is(arm)));
    }

    private String register(String participantId, String arm) throws Exception {
        String body = mvc.perform(post("/api/handover")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(new HandoverController.RegisterRequest(
                                participantId, arm, "Alex Morgan", "https://qualtrics.example/return"))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return mapper.readTree(body).get("token").asString();
    }
}
