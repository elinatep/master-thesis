import { useState } from 'react'
import { completeSession } from './log'
import { getCondition } from './session'

/** Appends the participant id to the callback URL as a query param (preserving any existing params). */
function withParticipantId(callbackUrl: string, participantId: string): string {
  const url = new URL(callbackUrl, window.location.origin)
  url.searchParams.set('participantId', participantId)
  return url.toString()
}

// Shared handback: close the study session, then return the participant to Qualtrics. Used by
// both the level 0-2 navbar (AppLayout) and the level 3 voice-only screen, so the behaviour is
// identical. Task success is left null (a manual finish) — derived in analysis from the events.
export function useHandback() {
  const [finishing, setFinishing] = useState(false)

  async function finish() {
    if (!window.confirm('Finish the task and return to the survey?')) return
    setFinishing(true)
    await completeSession()
    const condition = getCondition()
    const callbackUrl = condition?.callbackUrl
    if (callbackUrl) {
      window.location.href = withParticipantId(callbackUrl, condition.participantId)
    } else {
      // Dev / no callback URL: nothing to return to. Surface it rather than silently doing nothing.
      window.alert('Session marked complete. (No callback URL was provided, so staying here.)')
      setFinishing(false)
    }
  }

  return { finishing, finish }
}
