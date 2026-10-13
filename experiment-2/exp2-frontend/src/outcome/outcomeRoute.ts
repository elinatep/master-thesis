/**
 * Where the study takes over from the portal.
 *
 * The participant files their claim in the portal's own form. What Salvena then says about it is
 * the study's business, not the portal's - so the study watches for the portal reporting the
 * submission and moves them to its own screen.
 */
export const OUTCOME_PATH = '/outcome'

/**
 * Should this portal event hand control to the study?
 *
 * Only a real submission. Unlike the pre-test - which had an arm where the claim was deliberately
 * refused, and so also had to treat a submit error as an ending - here the claim always succeeds.
 * An ERROR from the claim form is therefore a genuine fault, and dressing it up as the
 * assessment would hand the participant a scripted outcome for a claim that does not exist.
 */
export function isClaimOutcomeEvent(type: string): boolean {
  return type === 'CLAIM_SUBMITTED'
}
