package solutions.andreas.study.voice;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

/**
 * The AI-side manipulation is assembled here, from files, at request time. These tests are the only
 * thing standing between a prompt-file mistake and a study run in which some participants were in a
 * condition nobody assigned them to - which would be invisible in the data, because the
 * behavioural log records the cell that was *intended*, not the prompt that was actually sent.
 *
 * <p>So they check three separate things: that each cell gets its own acknowledgement block, that
 * neither cell leaks the other's, and that everything else is byte-identical between them.
 */
class VoiceSessionCompositionTest {

    /** Real configuration/ dir, not a fixture: these tests exist to catch edits to the real files. */
    private static final String CONFIG_DIR = "../configuration";

    private final VoiceController controller =
            new VoiceController("test-key", "http://localhost:0", CONFIG_DIR, new ObjectMapper());

    @Test
    void acknowledgeCellGetsTheAcknowledgementBlock() {
        assertThat(instructions(true))
                .contains("Name what you are hearing")
                .contains("Acknowledging is not agreeing");
    }

    @Test
    void noAcknowledgeCellGetsTheProceduralBlock() {
        assertThat(instructions(false))
                .contains("Keep the call procedural")
                .contains("Do not name, reflect back, or comment on the customer's emotional state");
    }

    @Test
    void neitherCellLeaksTheOthersBlock() {
        assertThat(instructions(true)).doesNotContain("Keep the call procedural");
        assertThat(instructions(false)).doesNotContain("Name what you are hearing");
    }

    /**
     * Cross-condition parity, enforced rather than trusted. Strip the acknowledgement block from
     * each cell's prompt and what remains must be identical - same task, same claim, same limits,
     * same transfer behaviour. If someone edits ai-base.md, both cells move together; if someone
     * adds a capability to one variant file, this fails.
     */
    @Test
    void everythingExceptTheAcknowledgementBlockIsIdentical() {
        String marker = "HOW TO RESPOND TO HOW THE CUSTOMER IS FEELING.";
        String ack = instructions(true);
        String noAck = instructions(false);

        assertThat(ack).contains(marker);
        assertThat(noAck).contains(marker);
        assertThat(ack.substring(0, ack.indexOf(marker)))
                .isEqualTo(noAck.substring(0, noAck.indexOf(marker)));
    }

    @Test
    void bothCellsGetTheSameSingleTransferTool() {
        assertThat(toolNames(true))
                .containsExactly("transfer_to_human_agent")
                .isEqualTo(toolNames(false));
    }

    @Test
    void bothCellsKeepTheSharedRealtimeSessionSettings() {
        for (boolean aiAck : List.of(true, false)) {
            Map<String, Object> session = controller.buildSession(aiAck);
            assertThat(session).containsEntry("model", "gpt-realtime");
            assertThat(session).containsEntry("tool_choice", "auto");
        }
    }

    private String instructions(boolean aiAck) {
        return (String) controller.buildSession(aiAck).get("instructions");
    }

    @SuppressWarnings("unchecked")
    private List<String> toolNames(boolean aiAck) {
        List<Map<String, Object>> tools =
                (List<Map<String, Object>>) controller.buildSession(aiAck).get("tools");
        return tools.stream().map(t -> (String) t.get("name")).toList();
    }
}
