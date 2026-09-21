/**
 * Where the study takes over from the portal.
 *
 * The participant files their claim in the portal's own form. What happens next is the
 * manipulation, and it is not the portal's business - so the study watches for the portal
 * reporting the submission and moves them here.
 */
export const OUTCOME_PATH = '/pretest/outcome'

/**
 * Should this portal event hand control to the study?
 *
 * Two events, because there are two ways a submission ends. The portal reports CLAIM_SUBMITTED
 * when a claim was really created (arms S0, S1, S2), and an ERROR from `claim_submit` when it was
 * not (arm S4, where the study's filter refused it). Both mean the participant has finished with
 * the form, and both are followed by the arm's own screen.
 *
 * Any other ERROR is left alone: a genuine bug in the portal must not be dressed up as the
 * experimental manipulation, or the S4 arm would silently acquire participants from every other
 * cell that happened to hit a fault.
 */
export function isClaimOutcomeEvent(type: string, data?: Record<string, unknown>): boolean {
  if (type === 'CLAIM_SUBMITTED') return true
  return type === 'ERROR' && data?.where === 'claim_submit'
}
