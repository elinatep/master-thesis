import { useState } from 'react'
import { completeSession } from './log'
import { getCondition } from './session'

/**
 * Message posted to the embedding page when the participant finishes.
 *
 * Namespaced because a Qualtrics page receives messages from anything it embeds, and an
 * unqualified `type: 'complete'` would be a coin toss. The listener should check `source`.
 */
export const COMPLETION_MESSAGE = 'salvena-pretest:complete'

/** Appends the participant id to the callback URL as a query param (preserving any existing params). */
function withParticipantId(callbackUrl: string, participantId: string): string {
  const url = new URL(callbackUrl, window.location.origin)
  url.searchParams.set('participantId', participantId)
  return url.toString()
}

/** True when this page is running inside an iframe (i.e. embedded in the Qualtrics question). */
function isEmbedded(): boolean {
  try {
    return window.self !== window.top
  } catch {
    // Cross-origin access to window.top throws, which itself means we are embedded.
    return true
  }
}

/**
 * Where a postMessage may safely be sent.
 *
 * The callback URL is the survey the participant came from, so its origin is the embedder we
 * expect. Falling back to '*' would broadcast the participant id to whatever happened to embed
 * the page, so it is only used when there is no callback URL at all - a dev or preview run.
 */
function embedderOrigin(callbackUrl: string | null): string {
  if (!callbackUrl) return '*'
  try {
    return new URL(callbackUrl, window.location.origin).origin
  } catch {
    return '*'
  }
}

/**
 * Finishing the study: close the session, then hand the participant back to Qualtrics.
 *
 * <p>Two ways back, because there are two ways in. Embedded in a Qualtrics question, the
 * participant never left - so the page posts a message to the survey, which advances to the next
 * block, and the emotion measures follow the outcome immediately. Opened as its own tab, there is
 * nowhere to post to, so it redirects to the callback URL as before.
 *
 * <p>The session is marked complete before either, and the same way in both, so the data does not
 * depend on how the study was embedded.
 *
 * <p>Task success is left null (a manual finish) - derived in analysis from the events.
 */
export function useHandback() {
  const [finishing, setFinishing] = useState(false)

  async function finish() {
    setFinishing(true)
    await completeSession()

    const condition = getCondition()
    const callbackUrl = condition?.callbackUrl ?? null

    if (isEmbedded()) {
      window.parent.postMessage(
        {
          source: 'salvena-pretest',
          type: COMPLETION_MESSAGE,
          participantId: condition?.participantId ?? null,
          arm: condition?.arm ?? null,
        },
        embedderOrigin(callbackUrl),
      )
      // Stay on the outcome screen with the button spent. The survey decides what happens next;
      // blanking the page here would leave the participant staring at nothing if the listener is
      // missing, and that failure is much easier to spot during piloting than a blank frame.
      return
    }

    if (callbackUrl && condition) {
      window.location.href = withParticipantId(callbackUrl, condition.participantId)
      return
    }

    // Dev / no callback URL: nothing to return to. Surface it rather than silently doing nothing.
    window.alert('Session marked complete. (No callback URL was provided, so staying here.)')
    setFinishing(false)
  }

  return { finishing, finish }
}
