package solutions.andreas.study.voice;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcAutoConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Both ends of the voice leg must address the same Azure OpenAI resource.
 *
 * <p>This application mints the ephemeral token against {@code openai.base-url}; the browser then
 * exchanges SDP against {@code realtimeUrl}. When the second was written in
 * {@code configuration/voice/client.json} by hand, moving the deployment to another resource
 * changed one and not the other - and the symptom is an authentication failure about a key that is
 * perfectly valid, several steps from the setting that caused it.
 */
@SpringBootTest
@Import(MockMvcAutoConfiguration.class)
@TestPropertySource(properties = "openai.base-url=https://exp2-example.openai.azure.com/openai/v1")
class VoiceClientConfigTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void theBrowserIsSentToTheSameResourceTheTokenIsMintedAgainst() throws Exception {
        mvc.perform(get("/api/voice/client-config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.realtimeUrl")
                        .value("https://exp2-example.openai.azure.com/openai/v1/realtime/calls"))
                // The rest of the file is still served as written.
                .andExpect(jsonPath("$.audio.echoCancellation").value(true))
                .andExpect(jsonPath("$.greeting").exists());
    }
}
