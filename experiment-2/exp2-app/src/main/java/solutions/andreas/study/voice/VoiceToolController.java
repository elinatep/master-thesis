package solutions.andreas.study.voice;

import solutions.andreas.portal.core.spi.PortalActionRegistry;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

/**
 * Relays the voice bot's function calls to the portal. The bot emits a function call over the
 * Realtime data channel, the browser posts it here, and this looks the action up in the
 * {@link PortalActionRegistry} and runs it.
 *
 * <p>Study-owned adapter, deliberately thin: it does JSON, HTTP and error shaping, and knows nothing
 * about policies or claims. The portal owns the actions themselves, which is what keeps the bot
 * <em>operating the portal</em> rather than reimplementing it — and lets a future study expose a
 * different subset of tools without the portal changing.
 *
 * <p>Every call returns HTTP 200 with either the result or an {@code {"error": ...}} object, because
 * the relay must always hand the model SOMETHING as the function output — otherwise the bot goes
 * silent or hallucinates one.
 */
@RestController
@RequestMapping("/api/voice")
public class VoiceToolController {

    private static final Logger log = LoggerFactory.getLogger(VoiceToolController.class);

    private final PortalActionRegistry actions;
    private final ObjectMapper mapper;

    public VoiceToolController(PortalActionRegistry actions, ObjectMapper mapper) {
        this.actions = actions;
        this.mapper = mapper;
    }

    @PostMapping(value = "/tool", produces = MediaType.APPLICATION_JSON_VALUE)
    public Object invoke(@RequestBody ToolCall call) {
        String name = call.name() == null ? "" : call.name();
        log.debug("Voice tool call: {} args={}", name, call.arguments());
        try {
            return actions.invoke(name, parseArgs(call.arguments()));
        } catch (ResponseStatusException e) {
            // A business rule said no (expired policy, wrong certificate type): the bot can explain it.
            log.warn("Voice tool '{}' rejected: {}", name, e.getReason());
            return error(e.getReason());
        } catch (IllegalArgumentException e) {
            // Bad or missing arguments, or an unknown action: the bot can correct and retry.
            log.warn("Voice tool '{}' bad input: {}", name, e.getMessage());
            return error(e.getMessage());
        } catch (Exception e) {
            log.error("Voice tool '{}' failed", name, e);
            return error("Could not complete the action: " + e.getMessage());
        }
    }

    /**
     * The action names the portal currently offers. Not used by the bot at runtime — it's here so
     * the per-level tool definitions in {@code configuration/} can be checked against reality.
     */
    @GetMapping(value = "/tools", produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> available() {
        return Map.of("actions", actions.names());
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseArgs(String arguments) {
        if (arguments == null || arguments.isBlank()) {
            return Map.of();
        }
        try {
            return mapper.readValue(arguments, Map.class);
        } catch (Exception e) {
            throw new IllegalArgumentException("Could not parse tool arguments as JSON");
        }
    }

    private static Map<String, Object> error(String message) {
        return Map.of("error", message == null ? "Unknown error" : message);
    }

    /** What the browser relays from a Realtime function_call item. */
    record ToolCall(String name, String arguments) {
    }
}
