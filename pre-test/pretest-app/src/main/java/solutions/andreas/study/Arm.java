package solutions.andreas.study;

/**
 * The experimental arm a participant is assigned to: which version of the claim outcome they see.
 *
 * <p>The pre-test compares four candidate service situations - a control, an unfair outcome, an
 * uncertain outcome, and a portal that fails - to find one that reliably produces the affect the
 * later customer-service study needs. So the condition is a named arm from an open set, not a
 * crossing of fixed factors: which arms exist, and every word each one shows, lives in
 * {@code configuration/arms/}. Choosing between them is the whole point of the exercise, and
 * rewording one should be a file edit and a restart.
 *
 * <p>What is not open is whether an arm is recognised. Qualtrics assigns arms by sending a name
 * over HTTP, so an unknown name is rejected at the handover rather than defaulted - a participant
 * in a condition nobody defined looks exactly like real data.
 */
public record Arm(String id) {

    public Arm {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Arm id must not be blank");
        }
        id = id.trim();
    }
}
