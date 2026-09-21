// The pre-test's own endpoint. The outcome is scripted server-side, per arm, so nothing here
// decides what the participant sees - it only asks, and renders what comes back.

import { participantHeaders } from '../study/headers'

/** One scripted step of an arm: what the portal appears to have done with this submission. */
export interface ScriptedStep {
  result: 'SUBMITTED' | 'ERROR'
  banner?: { kind: 'success' | 'error'; text: string }
  bannerDetail?: string
  retry?: { instruction: string; heading: string; notice: string }
  panel?: {
    heading: string
    rows: { label: string; value: string; emphasis?: boolean }[]
    body: string[]
  }
}

export interface Outcome {
  arm: string
  attemptNumber: number
  totalAttempts: number
  finalAttempt: boolean
  scripted: ScriptedStep
}

/**
 * The outcome of the submission the participant has just made.
 *
 * Asked of the server rather than carried over from the portal page, so a reload of the outcome
 * screen shows the same outcome, and a reload mid-retry in S4 does not restart the arm.
 */
export async function getOutcome(): Promise<Outcome> {
  const res = await fetch('/api/pretest/outcome', { headers: participantHeaders() })
  if (!res.ok) throw new Error(`Could not load the claim outcome (${res.status})`)
  return (await res.json()) as Outcome
}
