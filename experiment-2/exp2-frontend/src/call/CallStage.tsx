import { useEffect, useRef, useState } from 'react'
import VoiceWidget from '../voice/VoiceWidget'
import HumanCallPanel from './HumanCallPanel'
import { participantHeaders } from '../study/headers'
import { logEvent } from '../study/log'

/**
 * The one voice surface the participant sees, across both legs of the call.
 *
 * The assistant's panel until it transfers them, then the human call. One component owns the
 * switch because the two legs have to look like one conversation: this is the continuity the
 * survey measures ("I did not have to repeat important information"), and it is held constant
 * across all four cells - so nothing here may depend on the condition, and nothing here does.
 */

/** How often to ask whether the transfer has happened. */
const POLL_MS = 2000

/**
 * How long the assistant keeps talking after the transfer is registered.
 *
 * The transfer is a tool call: the backend answers it, and the model then speaks the outcome
 * ("I'm putting you through now"). Cutting the session the instant the handover appears would clip
 * that line mid-word, which is a worse handover than a short wait.
 *
 * Six seconds is a guess at the length of one sentence and should be checked at the pilot - listen
 * for whether the assistant is ever cut off, or whether there is dead air before the hold message.
 */
const TRANSFER_GRACE_MS = 6000

interface HandoverStatus {
  handoverId: number | null
  state: 'none' | 'waiting' | 'connecting' | 'connected'
  position: number
  callId: string | null
}

export default function CallStage() {
  const [transferred, setTransferred] = useState(false)
  const [aiLegOver, setAiLegOver] = useState(false)
  const graceTimer = useRef<number | null>(null)
  // Whether the transfer has already been acted on, so the grace clock starts exactly once.
  const observed = useRef(false)

  useEffect(() => {
    let cancelled = false

    async function poll() {
      try {
        const res = await fetch('/api/study/agent-handover', { headers: participantHeaders() })
        if (!res.ok) return
        const status = (await res.json()) as HandoverStatus
        if (cancelled || status.state === 'none') return

        // Guarded by a ref, not by the state updater. A setState updater has to be pure - React
        // may call it twice (it does in StrictMode) - and the first version of this started the
        // grace timer and wrote the log line inside one, so both could happen twice.
        if (observed.current) return
        observed.current = true
        logEvent('HANDOVER_OBSERVED', { state: status.state, position: status.position })
        setTransferred(true)
        graceTimer.current = window.setTimeout(() => setAiLegOver(true), TRANSFER_GRACE_MS)
      } catch {
        // A failed poll is not worth reporting: the next one is two seconds away, and the
        // participant is mid-call. If the transfer already happened, nothing here undoes it.
      }
    }

    void poll()
    const interval = window.setInterval(() => void poll(), POLL_MS)
    return () => {
      cancelled = true
      window.clearInterval(interval)
      if (graceTimer.current !== null) window.clearTimeout(graceTimer.current)
    }
  }, [])

  return (
    <>
      {/*
        During the grace window both panels exist, so the assistant can finish its sentence while
        the participant is already joining the call. Only one is visible: the widget is hidden
        rather than unmounted, because unmounting ends the AI session (useVoiceSession tears down on
        cleanup) and its audio element lives outside this tree, so hiding the card does not silence
        it.
      */}
      {!aiLegOver && (
        <div style={transferred ? { display: 'none' } : undefined}>
          <VoiceWidget />
        </div>
      )}
      {transferred && <HumanCallPanel />}
    </>
  )
}
