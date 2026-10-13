import { useState } from 'react'
import { completeSession } from './log'
import { getCondition } from './session'

export interface HandbackResult {
  /**
   * Whether the participant asked customer service to review the assessment.
   *
   * Carried back to Qualtrics in the URL because it decides whether the row is usable at all: a
   * participant who accepted the assessment never met either agent, so they are in no cell of the
   * 2x2. Qualtrics needs it to route and to filter, and it is in the behavioural log as well
   * (ESCALATION_CHOICE) so the two can be checked against each other.
   */
  escalated?: boolean
}

/**
 * Appends what Qualtrics needs to the callback URL, preserving any params already on it.
 *
 * Only `participantId` and `escalated`. Deliberately NOT the condition: Qualtrics assigned it and
 * already holds it, and sending it back would create a second source of truth that could disagree
 * with the first - which is exactly the bug the pre-test hit when the platform's `arm` collided
 * with the survey's own.
 */
function handbackUrl(callbackUrl: string, participantId: string, result: HandbackResult): string {
  const url = new URL(callbackUrl, window.location.origin)
  url.searchParams.set('participantId', participantId)
  if (result.escalated !== undefined) {
    url.searchParams.set('escalated', String(result.escalated))
  }
  return url.toString()
}

export interface HandbackOptions {
  /**
   * Ask before finishing. True for the navbar button, where a misclick would end the task early.
   * False where finishing is itself the deliberate click - accepting the assessment, or continuing
   * after the call has ended - because asking someone to confirm what they just decided reads as
   * the page doubting them.
   */
  confirm?: boolean
}

/**
 * Close the study session and return the participant to Qualtrics.
 *
 * Task success is left null: it is a manual finish either way, and whether the task went well is
 * derived in analysis from the events rather than asserted here.
 */
export function useHandback({ confirm = true }: HandbackOptions = {}) {
  const [finishing, setFinishing] = useState(false)

  async function finish(result: HandbackResult = {}) {
    if (confirm && !window.confirm('Finish the task and return to the survey?')) return
    setFinishing(true)
    await completeSession()
    const condition = getCondition()
    const callbackUrl = condition?.callbackUrl
    if (callbackUrl) {
      window.location.href = handbackUrl(callbackUrl, condition.participantId, result)
    } else {
      // Dev / no callback URL: nothing to return to. Surface it rather than silently doing nothing.
      window.alert('Session marked complete. (No callback URL was provided, so staying here.)')
      setFinishing(false)
    }
  }

  return { finishing, finish }
}
