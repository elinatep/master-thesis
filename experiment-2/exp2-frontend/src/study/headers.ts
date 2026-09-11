// The participant scoping the study puts on portal API requests. Study-owned: the portal knows only
// that its host supplies request headers (see host/portalHost.ts), not that they name a participant.

import { getCondition } from './session'

/**
 * Header that ties every business request to the participant, so the backend scopes data to their
 * own policies/claims. The participant id is fixed for the session (Qualtrics handoff).
 */
export function participantHeaders(): Record<string, string> {
  const pid = getCondition()?.participantId
  return pid ? { 'X-Participant-Id': pid } : {}
}
