package solutions.andreas.study;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * The arms this pre-test is comparing, read from {@code configuration/arms/*.json}.
 *
 * <p>Read once at startup rather than per request, unlike the task text. The arm definitions are
 * the manipulation: if one were edited midway through a live run, participants before and after
 * the edit would be in silently different conditions while carrying the same arm name in the data.
 * Loading at startup means changing an arm requires a restart, which is a visible event.
 *
 * <p>Failing to start when the directory is missing or a file is malformed is deliberate. A
 * pre-test that boots with three of its four arms would run, collect data, and only reveal the
 * problem at analysis.
 */
@Component
public class ArmCatalogue {

    private static final Logger log = LoggerFactory.getLogger(ArmCatalogue.class);

    private final Map<String, Map<String, Object>> arms = new TreeMap<>();

    @SuppressWarnings("unchecked")
    public ArmCatalogue(@Value("${app.config-dir}") String configDir, ObjectMapper mapper) {
        Path dir = Path.of(configDir).resolve("arms");
        try (Stream<Path> files = Files.list(dir)) {
            List<Path> jsons = files.filter(p -> p.getFileName().toString().endsWith(".json"))
                    .sorted(Comparator.comparing(Path::getFileName))
                    .toList();
            for (Path file : jsons) {
                Map<String, Object> arm = mapper.readValue(Files.readAllBytes(file), Map.class);
                Object id = arm.get("id");
                if (!(id instanceof String armId) || armId.isBlank()) {
                    throw new IllegalStateException("Arm file " + file.getFileName() + " has no id");
                }
                arms.put(armId, arm);
            }
        } catch (IOException e) {
            throw new IllegalStateException("Could not read arm definitions from " + dir, e);
        }
        if (arms.isEmpty()) {
            throw new IllegalStateException("No arms defined in " + dir + " - the pre-test has nothing to compare");
        }
        log.info("Loaded {} arms from {}: {}", arms.size(), dir, arms.keySet());
    }

    public boolean isKnown(String id) {
        return id != null && arms.containsKey(id.trim());
    }

    /** For the fail-loud message: what the caller could have sent instead. */
    public Set<String> known() {
        return arms.keySet();
    }

    /** The full definition of an arm, as the frontend renders it. */
    public Map<String, Object> definition(Arm arm) {
        Map<String, Object> def = arms.get(arm.id());
        if (def == null) {
            throw new IllegalArgumentException("Unknown arm " + arm.id());
        }
        return def;
    }

    /**
     * What happens on the participant's {@code attemptNumber}-th submission (1-based), for this
     * arm. Attempts past the last defined one repeat the last: the flow should end there, but a
     * double-submit must not fall off the end of the script.
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> attempt(Arm arm, int attemptNumber) {
        List<Map<String, Object>> attempts = (List<Map<String, Object>>) definition(arm).get("attempts");
        int index = Math.min(Math.max(attemptNumber, 1), attempts.size()) - 1;
        return attempts.get(index);
    }

    /** How many submissions this arm scripts before the participant moves on. */
    @SuppressWarnings("unchecked")
    public int attemptCount(Arm arm) {
        return ((List<Map<String, Object>>) definition(arm).get("attempts")).size();
    }
}
