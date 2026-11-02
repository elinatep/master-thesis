package solutions.andreas.study.voice;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;
import solutions.andreas.study.call.HandoverGuard;
import tools.jackson.databind.ObjectMapper;

/**
 * Live transcription of the second leg of the call.
 *
 * <p>The AI leg transcribes itself: Realtime is already listening, and the finished lines arrive as
 * events. The human leg is two browsers on an Azure Communication Services line and nothing is
 * listening at all, so there has been no record of what was said on it - which is the half of the
 * call the study actually manipulates. {@code acknowledgement_slipped} in the session log has so
 * far been asking the agent to remember.
 *
 * <p>Each side transcribes its <b>own</b> microphone and posts the finished text. No audio is
 * stored by anyone, the same commitment the AI leg already keeps.
 *
 * <p>Two endpoints rather than one, because the two callers authenticate differently and neither
 * may be opened up to the other's route:
 * <ul>
 *   <li>{@code /api/agent/transcribe/token} - behind the agent credential with the rest of the
 *       console.</li>
 *   <li>{@code /api/call/transcribe/token} - participant-facing, and only while they have an open
 *       handover, exactly as the call token is. An unguarded minting endpoint on a public host is
 *       somebody else's transcription bill.</li>
 * </ul>
 *
 * <p>Everything about the session is read from {@code configuration/voice/transcription.json} at
 * request time. That is the same pattern as the assistant's own session config, and here it carries
 * extra weight: this was written against the documentation rather than a live endpoint, so the
 * shape may need correcting, and correcting it should not need a rebuild.
 */
@RestController
public class TranscribeController {

    private static final Logger log = LoggerFactory.getLogger(TranscribeController.class);

    private final RestClient openai;
    private final ObjectMapper mapper;
    private final Path configDir;
    private final HandoverGuard guard;
    private final String baseUrl;

    public TranscribeController(
            @Value("${openai.api-key}") String apiKey,
            @Value("${openai.base-url}") String baseUrl,
            @Value("${app.config-dir}") String configDir,
            ObjectMapper mapper,
            HandoverGuard guard) {
        this.mapper = mapper;
        this.configDir = Path.of(configDir);
        this.guard = guard;
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.openai = RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .build();
    }

    /** The agent's own microphone. Always on when a model is configured. */
    @PostMapping(value = "/api/agent/transcribe/token", produces = MediaType.APPLICATION_JSON_VALUE)
    public String agentToken() {
        return mint("agent");
    }

    /**
     * The participant's microphone on the human leg, and only if the configuration turns it on.
     *
     * <p>Off by default. Their audio already reaches this same Azure resource on the AI leg, so
     * transcribing the second leg is defensible - but it is new processing of participant speech
     * and that is a decision for the ethics application, not for a default.
     */
    @PostMapping(value = "/api/call/transcribe/token", produces = MediaType.APPLICATION_JSON_VALUE)
    public String participantToken() {
        Map<String, Object> config = readConfig();
        if (!Boolean.TRUE.equals(config.get("participantSide"))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Participant-side transcription is switched off");
        }
        // Same gate as the call token: no open handover, no call, nothing to transcribe.
        guard.requireOpenHandover();
        return mint("participant");
    }

    /** What the browser needs to open the socket, minus anything secret. */
    @GetMapping(value = "/api/call/transcribe/config", produces = MediaType.APPLICATION_JSON_VALUE)
    @SuppressWarnings("unchecked")
    public String clientConfig() {
        Map<String, Object> config = readConfig();
        Map<String, Object> client = new LinkedHashMap<>(
                config.get("client") instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.of());
        // The browser must not have to work out whether transcription is on; a page that decides
        // for itself is a page that disagrees with the server after someone edits the file.
        client.put("enabled", !modelOf(config).isBlank());
        client.put("participantSide", Boolean.TRUE.equals(config.get("participantSide")));
        // Derived here, not written in the file, for the reason the assistant's realtimeUrl is:
        // the token is minted against openai.base-url and has to be presented to the same resource.
        // A second copy in a config file is a copy that gets left behind when the deployment moves,
        // and the failure is an authentication error about a key that is perfectly valid.
        Object socketPath = client.get("socketPath");
        client.put("socketUrl", baseUrl.replaceFirst("^http", "ws")
                + (socketPath instanceof String p && !p.isBlank() ? p : "/realtime?intent=transcription"));
        return mapper.writeValueAsString(client);
    }

    @SuppressWarnings("unchecked")
    private String mint(String who) {
        Map<String, Object> config = readConfig();
        String model = modelOf(config);
        if (model.isBlank()) {
            // 503, not 500: nothing is broken, transcription is simply not configured. The hook
            // treats it as "do not transcribe" and the call proceeds untouched.
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "No transcription model configured");
        }

        Map<String, Object> session = new LinkedHashMap<>(
                config.get("session") instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.of());
        Map<String, Object> audio = new LinkedHashMap<>(
                session.get("audio") instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.of());
        Map<String, Object> input = new LinkedHashMap<>(
                audio.get("input") instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.of());

        // The model and languages are set here rather than written into the session block, so the
        // one value that has to match a real Azure deployment is a single top-level field in the
        // file instead of something buried three levels down.
        Map<String, Object> transcription = new LinkedHashMap<>();
        transcription.put("model", model);
        if (config.get("languages") != null) {
            // gpt-live-transcribe takes `languages`, plural - the older models took `language`.
            transcription.put("languages", config.get("languages"));
        }
        input.put("transcription", transcription);
        audio.put("input", input);
        session.put("audio", audio);

        String result = openai.post()
                .uri("/realtime/client_secrets")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("session", session))
                .retrieve()
                .body(String.class);
        log.debug("Transcription token minted for the {} side (model={})", who, model);
        return result;
    }

    private static String modelOf(Map<String, Object> config) {
        return config.get("model") instanceof String s ? s.trim() : "";
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> readConfig() {
        Path file = configDir.resolve("voice/transcription.json");
        try {
            return mapper.readValue(Files.readAllBytes(file), Map.class);
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Could not read transcription config from " + file, e);
        }
    }
}
