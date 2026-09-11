// Study intake: hand the arriving participant to the portal so it can provision (and, if they're
// new, seed) their account. Study-owned on purpose — the portal is told an account ref and a display
// name and nothing else; participants, handovers and conditions stay on this side of the line.

import { participantHeaders } from './headers'
import { getCondition } from './session'

/** Provision the portal account at intake: creates + seeds demo policies if the participant is new. */
export async function provisionUser(): Promise<void> {
  // Relay the Qualtrics ?name= so the persona (and the generated policy PDFs) use it. Optional.
  // URL-encoded: HTTP header values must be Latin-1, so an accented/Unicode name would otherwise
  // make fetch throw. The backend URL-decodes it.
  const name = getCondition()?.name
  const headers = { ...participantHeaders(), ...(name ? { 'X-Participant-Name': encodeURIComponent(name) } : {}) }
  const res = await fetch('/api/study/intake', { method: 'POST', headers })
  if (!res.ok) throw new Error(`Could not provision the participant (${res.status})`)
}
