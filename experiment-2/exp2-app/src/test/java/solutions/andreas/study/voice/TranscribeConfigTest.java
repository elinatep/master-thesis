package solutions.andreas.study.voice;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcAutoConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The guards in front of live transcription.
 *
 * <p>Two of them, and both are the kind that fail open if nobody looks: whether the participant's
 * own microphone is transcribed on the human leg is a decision for the ethics application, and the
 * minting endpoints sit on a public host where an open one is somebody else's transcription bill.
 */
@SpringBootTest
@Import(MockMvcAutoConfiguration.class)
@TestPropertySource(properties = {
        "openai.base-url=https://exp2-example.openai.azure.com/openai/v1",
        "app.agent.username=agent",
        "app.agent.password=ag3nt",
})
class TranscribeConfigTest {

    @Autowired
    private MockMvc mvc;

    /**
     * The socket is derived from the same base URL the token is minted against, for the reason
     * {@code VoiceClientConfigTest} exists: a hand-written second copy gets left behind when the
     * deployment moves, and the symptom is an authentication error about a valid key.
     */
    @Test
    void theBrowserIsSentToTheSameResourceTheTokenIsMintedAgainst() throws Exception {
        mvc.perform(get("/api/call/transcribe/config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.socketUrl")
                        .value("wss://exp2-example.openai.azure.com/openai/v1/realtime?intent=transcription"));
    }

    /**
     * The page is told whether transcription is on rather than deciding. A browser that works it
     * out for itself is a browser that disagrees with the server after someone edits the file.
     */
    @Test
    void theServerDecidesWhetherEitherSideIsTranscribed() throws Exception {
        mvc.perform(get("/api/call/transcribe/config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").exists())
                .andExpect(jsonPath("$.participantSide").exists());
    }

    /**
     * With both sides transcribed, the participant endpoint is still not open: a caller with no
     * study session and no handover gets nothing. It sits on a public host, and an open minting
     * endpoint is somebody else's transcription bill.
     */
    @Test
    void theParticipantSideStillNeedsASessionAndAnOpenHandover() throws Exception {
        mvc.perform(post("/api/call/transcribe/token"))
                .andExpect(status().is4xxClientError())
                .andExpect(status().is(not(403)));
    }

    /** The agent's own minting endpoint is behind the agent credential with the rest of console. */
    @Test
    void theAgentSideNeedsTheAgentCredential() throws Exception {
        mvc.perform(post("/api/agent/transcribe/token"))
                .andExpect(status().isUnauthorized());
    }
}
