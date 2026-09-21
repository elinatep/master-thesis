// The pre-test's own endpoints. The claim outcome is scripted server-side, per arm, so nothing
// here decides what the participant sees - it only asks, and renders what comes back.

import { participantHeaders } from '../study/headers'

/** One scripted step of an arm: what the portal appears to do on this submission. */
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

export interface FormState {
  arm: string
  attemptNumber: number
  totalAttempts: number
  /** The step the participant already saw, if they are resuming after a failure. */
  previousOutcome: ScriptedStep | null
}

export interface Outcome {
  arm: string
  attemptNumber: number
  totalAttempts: number
  finalAttempt: boolean
  scripted: ScriptedStep
}

export interface ClaimFields {
  typeOfDamage: string
  cause: string
  incidentDate: string
  estimatedDamage: string
}

/**
 * Where the participant stands before submitting. Asked on entry rather than assumed, so a reload
 * part-way through the two-failure arm resumes instead of restarting the script.
 */
export async function getFormState(): Promise<FormState> {
  const res = await fetch('/api/pretest/claim', { headers: participantHeaders() })
  if (!res.ok) throw new Error(`Could not load the claim form (${res.status})`)
  return (await res.json()) as FormState
}

export async function submitClaim(fields: ClaimFields): Promise<Outcome> {
  const res = await fetch('/api/pretest/claim', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', ...participantHeaders() },
    body: JSON.stringify(fields),
  })
  if (!res.ok) throw new Error(`Could not submit the claim (${res.status})`)
  return (await res.json()) as Outcome
}
