package solutions.andreas.study;

/**
 * The assigned experimental condition: Experiment 2's 2x2 between-subjects design.
 *
 * <p>Two independent factors, never one packed code:
 * <ul>
 *   <li>{@code aiAck} - whether the AI voice assistant acknowledges the negative affect the
 *       participant is feeling about the claim outcome;</li>
 *   <li>{@code humanAck} - whether the human employee acknowledges it after the handover.</li>
 * </ul>
 *
 * <p>Kept as two booleans all the way from the Qualtrics register call to the CSV export, because
 * the analysis is a 2x2 ANOVA: main effects and the interaction are read off the two factors
 * directly. Collapsing them into an ordinal level (as the levels-of-ai-help study legitimately
 * does, where the IV really is ordered) would have to be undone before every model.
 *
 * <p>Randomisation itself is Qualtrics' job - it already owns consent, screening and the survey,
 * and it registers the condition server-side before the participant is ever redirected here. This
 * application only receives, stores and renders what it was told.
 */
public record Condition(boolean aiAck, boolean humanAck) {

    /**
     * Short label for the cell, for logs and the researcher-facing export: {@code AI+/H-} and so
     * on. Convenience only - never parse it back; the two booleans are the data.
     */
    public String cell() {
        return (aiAck ? "AI+" : "AI-") + "/" + (humanAck ? "H+" : "H-");
    }
}
