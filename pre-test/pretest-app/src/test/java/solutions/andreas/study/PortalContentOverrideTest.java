package solutions.andreas.study;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * The portal is white-labelled by pointing {@code app.content.location} at
 * {@code configuration/portal-content/}, and that REPLACES the library's own content directory
 * rather than merging with it. Anything the default provides and the override does not is simply
 * absent: the newsroom loses an article, or a page renders with a broken image.
 *
 * <p>Nothing complains when that happens. The portal serves what it has, the participant sees a
 * missing picture on an insurer's website, and the thing being measured is partly their reaction to
 * a broken mock-up. It would also only appear once the image is deployed - the files are read from
 * the filesystem at request time, so a local run with the default content looks fine.
 *
 * <p>So this compares the two directories by name, and fails naming what is missing.
 */
class PortalContentOverrideTest {

    private static final Path OVERRIDE = Path.of("..", "configuration", "portal-content");

    @Test
    void theOverrideProvidesEveryFileTheLibraryDoes() throws IOException {
        Set<String> shipped = namesUnder("classpath*:portal-content/**");
        Set<String> ours = namesUnder("file:" + OVERRIDE.toAbsolutePath().normalize() + "/**");

        assertThat(shipped)
                .as("sanity: the library's default content should be on the test classpath")
                .isNotEmpty();

        Set<String> missing = new TreeSet<>(shipped);
        missing.removeAll(ours);
        assertThat(missing)
                .as("files the library provides that configuration/portal-content/ does not - the "
                        + "override replaces the directory wholesale, so each of these would be "
                        + "missing from the deployed portal")
                .isEmpty();
    }

    /**
     * The override exists to make the portal British. A Swiss marker left in it means a participant
     * reading a Zürich insurer's website about their Manchester household policy - which is the
     * realism this study runs on, not a cosmetic detail.
     */
    @Test
    void theOverrideCarriesNoSwissMarkers() throws IOException {
        for (Path file : List.of(OVERRIDE.resolve("company.json"), OVERRIDE.resolve("news.json"))) {
            String text = Files.readString(file);
            assertThat(text)
                    .as("%s should name no Swiss places, currency or company form", file.getFileName())
                    .doesNotContain("Zürich", "Zurich", "CHF", "Versicherungen", "+41");
        }
    }

    /** The company the participant is dealing with, as the portal will present it. */
    @Test
    void theCompanyIsBritish() throws IOException {
        JsonNode company = new ObjectMapper().readTree(Files.readString(OVERRIDE.resolve("company.json")));
        assertThat(company.get("legalName").asString()).contains("Ltd");
        assertThat(company.get("contact").get("phone").asString()).startsWith("+44");
    }

    private static Set<String> namesUnder(String pattern) throws IOException {
        Set<String> names = new TreeSet<>();
        for (Resource r : new PathMatchingResourcePatternResolver().getResources(pattern)) {
            String name = r.getFilename();
            // Directories come back with an empty or null filename depending on the protocol.
            if (name != null && !name.isBlank() && r.isReadable()) {
                names.add(name);
            }
        }
        return names;
    }
}
