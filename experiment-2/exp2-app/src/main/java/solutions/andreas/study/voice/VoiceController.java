package solutions.andreas.study.voice;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import tools.jackson.databind.ObjectMapper;

/**
 * Mints short-lived ephemeral client secrets so the browser can open a Realtime
 * WebRTC session directly with OpenAI - the long-lived key never leaves the server.
 * GA interface: POST /v1/realtime/client_secrets (no beta header).
 *
 * The session config is not hardcoded: it is read at request time from the top-level
 * configuration/ dir - session.json (model/voice/audio) overlaid with the instructions
 * file for the assigned cell. Editing those files tunes the bot without a rebuild, which
 * matters more here than usual: the instructions ARE the manipulation.
 */
@RestController
@RequestMapping("/api/voice")
public class VoiceController {

    private static final Logger log = LoggerFactory.getLogger(VoiceController.class);

    private final RestClient openai;
    private final ObjectMapper mapper;
    private final Path configDir;

    public VoiceController(
            @Value("${openai.api-key}") String apiKey,
            @Value("${openai.base-url}") String baseUrl,
            @Value("${app.config-dir}") String configDir,
            ObjectMapper mapper) {
        this.mapper = mapper;
        this.configDir = Path.of(configDir);
        this.openai = RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .build();
    }

    /**
     * Returns the OpenAI client_secrets response (browser reads the ephemeral token from
     * {@code .value}). Only the AI-acknowledgement factor reaches this endpoint: it is the one
     * that shapes what the voice assistant says. The human-acknowledgement factor is delivered to
     * the human agent in the console, and must never leak into the AI's prompt - the AI is not
     * supposed to know how its colleague is going to behave.
     */
    @PostMapping(value = "/token", produces = MediaType.APPLICATION_JSON_VALUE)
    public String token(@RequestParam boolean aiAck) {
        log.debug("Minting realtime ephemeral token (aiAck={})", aiAck);
        Map<String, Object> session = buildSession(aiAck);
        String result = openai.post()
                .uri("/realtime/client_secrets")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("session", session))
                .retrieve()
                .body(String.class);
        log.debug("Realtime ephemeral token minted (aiAck={})", aiAck);
        return result;
    }

    /**
     * Browser-side voice config (mic capture constraints, the Realtime SDP URL), read from
     * configuration/voice/client.json so ALL voice config lives in one traceable place rather
     * than being hardcoded in the React app. Served as-is for the frontend to apply.
     */
    @GetMapping(value = "/client-config", produces = MediaType.APPLICATION_JSON_VALUE)
    public String clientConfig() {
        try {
            return Files.readString(configDir.resolve("voice/client.json"));
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Could not read voice client config from " + configDir, e);
        }
    }

    /**
     * session.json overlaid with the cell's instructions and the shared tool set.
     *
     * <p>The instructions are <b>composed</b>, not picked: {@code ai-base.md} holds everything the
     * two cells share - language, accent, what is on screen, the claim, how to run the dispute,
     * when to transfer - and {@code ai-ack.md} / {@code ai-noack.md} hold nothing but the
     * acknowledgement block. The base is the same bytes in both cells because it is literally the
     * same file.
     *
     * <p>This is a deliberate departure from the per-condition prompt files in levels-of-ai-help.
     * There, each level's prompt genuinely describes a different assistant. Here the two prompts
     * would be near-identical twins differing in one paragraph, and keeping two long duplicates in
     * step by hand is a losing game: the first typo fixed in one file and not the other becomes a
     * silent confound that no test catches and no reviewer sees. Composition makes cross-condition
     * parity structural rather than a matter of discipline.
     *
     * <p>The tools file is one file for both cells for the same reason. The assistant can do
     * exactly the same things whether or not it acknowledges how the participant feels - including
     * handing over full context, which is continuity, not acknowledgement. A capability difference
     * that crept in alongside the manipulation would be indistinguishable from the effect being
     * measured once the data is in.
     *
     * <p>Fails loud if any part is missing - a session that silently fell back to a default prompt
     * would be an unassignable data point.
     */
    // Package-private rather than private so the composition can be tested directly. This is
    // where the manipulation is actually assembled; a silent mistake here (the wrong block, a
    // block dropped, the base not shared) would run the study with a condition nobody assigned,
    // and nothing downstream would show it.
    @SuppressWarnings("unchecked")
    Map<String, Object> buildSession(boolean aiAck) {
        String variant = aiAck ? "ai-ack" : "ai-noack";
        try {
            byte[] base = Files.readAllBytes(configDir.resolve("voice/session.json"));
            Map<String, Object> session = mapper.readValue(base, Map.class);

            String shared = Files.readString(configDir.resolve("voice/instructions/ai-base.md"));
            String acknowledgement = Files.readString(
                    configDir.resolve("voice/instructions/" + variant + ".md"));
            session.put("instructions", shared.stripTrailing() + "\n\n" + acknowledgement.strip() + "\n");

            Path toolsFile = configDir.resolve("voice/tools/ai.json");
            if (Files.exists(toolsFile)) {
                session.put("tools", mapper.readValue(Files.readAllBytes(toolsFile), List.class));
                session.put("tool_choice", "auto");
            }
            return session;
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Could not read voice configuration for " + variant + " from " + configDir, e);
        }
    }
}
