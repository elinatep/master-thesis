package solutions.andreas.study;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import tools.jackson.databind.ObjectMapper;

/**
 * What Salvena tells the participant about the claim they just filed, read from
 * {@code configuration/study/outcome.json}.
 *
 * <p>Every participant sees the same thing. The pre-test compared four outcome scripts to find one
 * that reliably produces anger without reading as a bug; this study takes the one that worked (its
 * S1, "anger - unfair outcome") and holds it constant, because here the outcome is the *setup* and
 * the manipulation happens afterwards, on the call.
 *
 * <p>Read once at startup rather than per request, unlike the task text. This wording is what makes
 * the participant want to dispute, so editing it midway through a live run would mean participants
 * before and after the edit meeting materially different situations while their data says nothing
 * about which. A restart is a visible event; a file edit is not.
 *
 * <p>Failing to start when the file is missing or malformed is deliberate: a deployment that boots
 * without an outcome would take claims, show nothing, and only reveal it with a participant on the
 * screen.
 */
@Component
public class ClaimOutcome {

    private static final Logger log = LoggerFactory.getLogger(ClaimOutcome.class);

    private final Map<String, Object> scripted;

    @SuppressWarnings("unchecked")
    public ClaimOutcome(@Value("${app.config-dir}") String configDir, ObjectMapper mapper) {
        Path file = Path.of(configDir).resolve("study").resolve("outcome.json");
        try {
            scripted = mapper.readValue(Files.readAllBytes(file), Map.class);
        } catch (IOException e) {
            throw new IllegalStateException("Could not read the claim outcome from " + file, e);
        }
        if (scripted == null || scripted.isEmpty()) {
            throw new IllegalStateException(file + " is empty - there is nothing to show the "
                    + "participant after they file their claim");
        }
        if (!(scripted.get("panel") instanceof Map<?, ?>)) {
            // The panel is the assessment itself. Without it the participant files a claim and is
            // told nothing, which is not a condition this study has.
            throw new IllegalStateException(file + " has no 'panel' - that is the assessment the "
                    + "participant is shown, and the whole situation rests on it");
        }
        log.info("Loaded the claim outcome from {}", file);
    }

    /** The script, passed through untouched - banner, detail, assessment panel, escalation copy. */
    public Map<String, Object> scripted() {
        return scripted;
    }
}
