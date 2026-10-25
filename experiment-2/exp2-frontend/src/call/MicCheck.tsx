import { useCallback, useEffect, useRef, useState } from 'react'
import { logEvent } from '../study/log'

/**
 * The microphone check, between asking for a review and being put through.
 *
 * <p>Four hundred people on their own machines, and the call is the study. A participant whose
 * microphone is muted in hardware, pointed at the wrong device, or simply never permitted does not
 * find out until a confederate is already on the line listening to silence - and what that costs is
 * not one lost row, it is the agent's time and a participant who has done ten minutes of work for
 * a call that could not happen.
 *
 * <p>Permission is per-origin, so a check in Qualtrics proves nothing here: only this page can ask
 * for this page. It is asked at the escalation rather than at entry because this is the first
 * moment the microphone is genuinely needed, and a browser prompt that arrives before there is any
 * reason for it gets refused more often.
 *
 * <p>It does not block on a quiet meter. A level that never registers may be a quiet microphone
 * rather than a broken one, and stranding somebody on our own threshold is worse than letting a
 * difficult call through. A refused permission IS a stop, because there is nothing to put through.
 */

/** Above this, treated as "we can hear you". Chosen low: the aim is a sign of life, not quality. */
const SPEAKING_LEVEL = 0.04

/** How long a level has to hold before the check passes, so a single click does not count. */
const SPEAKING_MS = 300

type CheckState =
  | 'idle'
  | 'asking'
  /** Permission granted, listening - we have not heard anything above the threshold yet. */
  | 'listening'
  | 'heard'
  | 'denied'
  | 'unavailable'

export default function MicCheck({ onReady, onGiveUp }: { onReady: () => void; onGiveUp: () => void }) {
  const [state, setState] = useState<CheckState>('idle')
  const [level, setLevel] = useState(0)
  const [detail, setDetail] = useState<string | null>(null)

  const streamRef = useRef<MediaStream | null>(null)
  const contextRef = useRef<AudioContext | null>(null)
  const frameRef = useRef<number | null>(null)
  const loudSinceRef = useRef<number | null>(null)
  const heardRef = useRef(false)

  const stop = useCallback(() => {
    if (frameRef.current !== null) cancelAnimationFrame(frameRef.current)
    frameRef.current = null
    streamRef.current?.getTracks().forEach((t) => t.stop())
    streamRef.current = null
    void contextRef.current?.close()
    contextRef.current = null
  }, [])

  // Release the microphone when this goes away. The call acquires its own a moment later, and two
  // live captures of the same device is how a participant ends up inaudible on the call itself.
  useEffect(() => stop, [stop])

  const start = useCallback(async () => {
    setState('asking')
    setDetail(null)
    try {
      if (!navigator.mediaDevices?.getUserMedia) {
        // Old browser, or an insecure context. Nothing to retry.
        logEvent('MIC_CHECK', { result: 'unavailable' })
        setState('unavailable')
        return
      }

      const stream = await navigator.mediaDevices.getUserMedia({ audio: true })
      streamRef.current = stream
      logEvent('MIC_CHECK', { result: 'granted' })
      setState('listening')

      const context = new AudioContext()
      contextRef.current = context
      const analyser = context.createAnalyser()
      analyser.fftSize = 512
      context.createMediaStreamSource(stream).connect(analyser)
      const samples = new Float32Array(analyser.fftSize)

      const tick = () => {
        analyser.getFloatTimeDomainData(samples)
        // Root mean square: loudness over the window rather than one sample, so a click does not
        // read the same as speech.
        let sum = 0
        for (const s of samples) sum += s * s
        const rms = Math.sqrt(sum / samples.length)
        setLevel(rms)

        if (rms >= SPEAKING_LEVEL) {
          const now = performance.now()
          loudSinceRef.current ??= now
          if (!heardRef.current && now - loudSinceRef.current >= SPEAKING_MS) {
            heardRef.current = true
            logEvent('MIC_CHECK', { result: 'heard' })
            setState('heard')
          }
        } else {
          loudSinceRef.current = null
        }
        frameRef.current = requestAnimationFrame(tick)
      }
      frameRef.current = requestAnimationFrame(tick)
    } catch (e) {
      const message = e instanceof Error ? e.message : String(e)
      logEvent('MIC_CHECK', { result: 'denied', error: message })
      setDetail(message)
      setState('denied')
    }
  }, [])

  function proceed() {
    // Whether we actually heard them is recorded either way - a call that goes ahead on an
    // unconfirmed microphone is exactly the session you want to be able to find afterwards.
    logEvent('MIC_CHECK_PASSED', { heard: heardRef.current })
    stop()
    onReady()
  }

  if (state === 'idle') {
    return (
      <Card>
        <p>
          Customer service will speak with you by voice, so your microphone needs to be on. This
          takes a few seconds.
        </p>
        <button className="btn btn-primary btn-lg" onClick={() => void start()}>
          Check my microphone
        </button>
      </Card>
    )
  }

  if (state === 'denied' || state === 'unavailable') {
    return (
      <Card>
        <p className="text-danger mb-2">
          {state === 'denied'
            ? 'Your browser did not allow access to the microphone.'
            : 'This browser cannot use a microphone on this page.'}
        </p>
        <p className="mb-3">
          {state === 'denied'
            ? 'Allow microphone access for this site — in Safari, Settings for This Website → Microphone — then try again. Without it, customer service cannot hear you.'
            : 'Try a different browser, or continue without the review.'}
        </p>
        {detail && <p className="small text-muted">{detail}</p>}
        <div className="d-flex gap-2">
          {state === 'denied' && (
            <button className="btn btn-primary" onClick={() => void start()}>
              Try again
            </button>
          )}
          {/*
            An exit that is not the call. Somebody who cannot get their microphone working must be
            able to finish and be paid, rather than being held at a screen they cannot pass.
          */}
          <button className="btn btn-outline-secondary" onClick={onGiveUp}>
            Accept the assessment instead
          </button>
        </div>
      </Card>
    )
  }

  const heard = state === 'heard'
  return (
    <Card>
      <p className="mb-2">{heard ? 'We can hear you.' : 'Say something — "hello" is enough.'}</p>
      <div className="progress mb-3" style={{ height: 10 }} role="presentation">
        <div
          className={`progress-bar ${heard ? 'bg-success' : ''}`}
          style={{ width: `${Math.min(100, Math.round((level / (SPEAKING_LEVEL * 2)) * 100))}%` }}
        />
      </div>
      <div className="d-flex gap-2">
        <button className={`btn btn-lg ${heard ? 'btn-primary' : 'btn-outline-primary'}`} onClick={proceed}>
          Put me through
        </button>
      </div>
      {!heard && (
        <p className="small text-muted mt-2 mb-0">
          If the bar stays still, check that the right microphone is selected and not muted. You can
          continue anyway, but customer service may not be able to hear you.
        </p>
      )}
    </Card>
  )
}

function Card({ children }: { children: React.ReactNode }) {
  return (
    <div className="card mt-3">
      <div className="card-body">{children}</div>
    </div>
  )
}
