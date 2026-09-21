package solutions.andreas.study;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Serves the task the participant is asked to carry out, read at request time from
 * {@code configuration/study/task.md}.
 *
 * <h2>Why it is served rather than bundled</h2>
 * The task is the stimulus: it has to match, word for word, what Qualtrics told the participant
 * immediately before it handed them over. Wording that lives in the frontend bundle can only be
 * corrected by a rebuild, a republish and a redeploy - too slow to keep in step with a survey being
 * edited, and too easy to leave stale. Read from the filesystem it is one file edit and a restart,
 * and it sits with the voice instructions, so everything a participant is shown or told is in one
 * place when the study is written up.
 *
 * <h2>Not participant-specific</h2>
 * Every participant gets the same task in every condition - that is what makes the conditions
 * comparable - so this needs no session, no participant id and no authentication, and it is exactly
 * as public as the SPA that renders it.
 */
@RestController
@RequestMapping("/api/study")
public class TaskController {

    private static final Logger log = LoggerFactory.getLogger(TaskController.class);

    private final Path configDir;

    public TaskController(@Value("${app.config-dir}") String configDir) {
        this.configDir = Path.of(configDir);
    }

    /**
     * The task text as Markdown (a leading {@code # } heading, then paragraphs - see the frontend's
     * task.ts for the subset that is rendered).
     *
     * Fails loud with a 500 rather than returning an empty card: a participant working without the
     * task in front of them is the situation this whole feature exists to prevent, so it should be
     * obvious in testing rather than quietly degrade during a run.
     */
    @GetMapping(value = "/task", produces = "text/markdown;charset=UTF-8")
    public String task() {
        Path file = configDir.resolve("study/task.md");
        try {
            return Files.readString(file);
        } catch (IOException e) {
            log.error("Could not read the participant task from {}", file, e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Could not read the participant task from " + file, e);
        }
    }
}
