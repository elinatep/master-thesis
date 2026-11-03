package solutions.andreas.study.voice;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcAutoConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The off switch for participant-side transcription.
 *
 * <p>The shipped configuration transcribes both sides: the ethics approval permits transcription
 * and forbids recording, and nothing is recorded - the samples go to the recogniser and only the
 * text comes back. But the switch has to work, and it has to work <b>at the server</b>. A page that
 * is merely well behaved is not a control, because the endpoint is reachable without it.
 *
 * <p>Runs against a copy of the real configuration with the one flag flipped, rather than a
 * cut-down fixture: the application reads the claim outcome, the portal content and the seed from
 * this directory at startup and fails loud when any of it is missing, which is the right behaviour
 * and makes a partial fixture unusable.
 */
@SpringBootTest
@Import(MockMvcAutoConfiguration.class)
class TranscribeOffTest {

    @TempDir
    static Path configDir;

    @DynamicPropertySource
    static void switchParticipantSideOff(DynamicPropertyRegistry registry) throws IOException {
        Path real = Path.of("../configuration");
        try (Stream<Path> tree = Files.walk(real)) {
            for (Path source : tree.toList()) {
                Path target = configDir.resolve(real.relativize(source).toString());
                if (Files.isDirectory(source)) {
                    Files.createDirectories(target);
                } else {
                    Files.createDirectories(target.getParent());
                    Files.copy(source, target);
                }
            }
        }
        Path file = configDir.resolve("voice/transcription.json");
        Files.writeString(file, Files.readString(file)
                .replace("\"participantSide\": true", "\"participantSide\": false"));
        registry.add("app.config-dir", configDir::toString);
    }

    @Test
    void theParticipantSideIsRefusedWhenItIsSwitchedOff() throws Exception {
        mvc.perform(post("/api/call/transcribe/token"))
                .andExpect(status().isForbidden());
    }

    @Test
    void andThePageIsToldSoRatherThanGuessing() throws Exception {
        mvc.perform(get("/api/call/transcribe/config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.participantSide").value(false));
    }

    @Autowired
    private MockMvc mvc;
}
