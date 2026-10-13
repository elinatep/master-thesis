// The scripted claim outcome. Decided server-side, from configuration/study/outcome.json, so
// nothing here chooses what the participant sees - it only asks, and renders what comes back.

import { participantHeaders } from '../study/headers'

export interface ScriptedOutcome {
  banner?: { kind: 'success' | 'error'; text: string }
  bannerDetail?: string
  panel?: {
    heading: string
    rows: { label: string; value: string; emphasis?: boolean }[]
    body: string[]
  }
  /** The wording on the two buttons, and the line above them. */
  escalation?: { contact: string; accept: string; note?: string }
}

export interface Outcome {
  scripted: ScriptedOutcome
}

/**
 * The outcome of the claim the participant has just filed.
 *
 * Asked of the server rather than carried over from the portal page, so reloading this screen
 * shows the same assessment - and so that nobody reaches it by typing the URL without having
 * filed anything.
 */
export async function getOutcome(): Promise<Outcome> {
  const res = await fetch('/api/study/outcome', { headers: participantHeaders() })
  if (res.status === 409) {
    throw new Error('No claim has been filed yet.')
  }
  if (!res.ok) {
    throw new Error(`Could not load the claim outcome (${res.status})`)
  }
  return (await res.json()) as Outcome
}
