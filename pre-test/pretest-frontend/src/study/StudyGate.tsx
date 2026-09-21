import { useEffect, useRef, useState } from 'react'
import type { ReactNode } from 'react'
import { useLocation } from 'react-router-dom'
import { describeInvalidCondition, resolveCondition } from './session'
import { createSession } from './log'
import { provisionUser } from './intake'

// Bootstraps the study session before the portal renders. Strict intake: with no participantId we
// show an error and never open a session (a mis-parsed arm must fail loudly, not log orphaned
// data). Once the session is open, the rest of the app can log freely.
//
// /data is exempt: it is the researcher's export, not part of a participant run, so it must be
// reachable without a participant session and must not create one.

type GateState = 'loading' | 'ready' | 'invalid' | 'error'

export default function StudyGate({ children }: { children: ReactNode }) {
  const isExempt = useLocation().pathname.startsWith('/data')
  const [state, setState] = useState<GateState>('loading')
  const [detail, setDetail] = useState('')
  const startedRef = useRef(false)

  useEffect(() => {
    if (isExempt) return // no participant session for the researcher's export
    if (startedRef.current) return // guard StrictMode's double-invoke so we open one session
    startedRef.current = true
    // Exchange the handover token for the arm, open the behavioural session, then provision the
    // portal account so the participant has the policies the scenario assumes. All three must
    // succeed before the portal renders: an unprovisioned account would show an empty policy list
    // to someone who has just been told which policy to claim against. An invalid or expired token
    // fails loud (no session opened).
    resolveCondition()
      .then((cond) => {
        if (!cond) {
          setState('invalid')
          return
        }
        return createSession(cond)
          .then(() => provisionUser())
          .then(() => setState('ready'))
      })
      .catch((e: unknown) => {
        setDetail(e instanceof Error ? e.message : String(e))
        setState('error')
      })
  }, [isExempt])

  if (isExempt) return <>{children}</>
  if (state === 'ready') return <>{children}</>
  if (state === 'loading') return <Screen title="Starting…" />
  if (state === 'invalid') {
    return (
      <Screen
        error
        title="Can't start the session"
        body={`Could not start from the study handover: ${describeInvalidCondition()}. This page must be opened from the survey link, which carries a valid ?t=.`}
      />
    )
  }
  return <Screen error title="Couldn't start the session" body={detail} />
}

function Screen({ title, body, error }: { title: string; body?: string; error?: boolean }) {
  return (
    <div
      style={{
        minHeight: '100vh',
        display: 'flex',
        flexDirection: 'column',
        alignItems: 'center',
        justifyContent: 'center',
        gap: 12,
        textAlign: 'center',
        padding: 24,
      }}
    >
      <h1 className={`h4 mb-0 ${error ? 'text-danger' : ''}`}>{title}</h1>
      {body && <p className="text-muted mb-0" style={{ maxWidth: 460 }}>{body}</p>}
    </div>
  )
}
