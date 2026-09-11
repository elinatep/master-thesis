import { Button, Spinner } from 'react-bootstrap'
import { useVoiceSession, type VoiceStatus } from '../voice/useVoiceSession'
import { useHandback } from './useHandback'

// Level 3 (voice-only): no portal GUI is rendered. The bot is the entire interface and
// acts via server-side tools, so this page just makes the session tangible to the
// participant — it shows whether the assistant is live and lets them start/stop it.

const STATUS_TEXT: Record<VoiceStatus, string> = {
  idle: 'Assistant ready',
  connecting: 'Connecting…',
  live: 'Assistant is live — just speak',
  error: 'Something went wrong',
}

export default function VoiceOnlyPage() {
  const { status, error, start, stop } = useVoiceSession()
  const { finishing, finish } = useHandback()
  const busy = status === 'connecting' || status === 'live'

  return (
    <div
      style={{
        minHeight: '100vh',
        display: 'flex',
        flexDirection: 'column',
        alignItems: 'center',
        justifyContent: 'center',
        gap: 24,
        textAlign: 'center',
        padding: 24,
        background: '#0d1b2a',
        color: '#e0e1dd',
      }}
    >
      {/* Handback: no navbar here, so the Finish control lives on the voice screen itself. */}
      <div style={{ position: 'fixed', top: 16, left: 16 }}>
        <Button size="sm" variant="outline-light" disabled={finishing} onClick={finish}>
          {finishing ? 'Finishing…' : 'Finish'}
        </Button>
      </div>

      <Orb status={status} />

      <div>
        <h1 className="h4 mb-1">Voice Assistant</h1>
        <p className="mb-0" style={{ opacity: 0.8 }}>
          {STATUS_TEXT[status]}
        </p>
      </div>

      {error && <div className="text-danger small">{error}</div>}

      {busy ? (
        <Button variant="outline-light" size="lg" onClick={stop}>
          {status === 'connecting' && <Spinner size="sm" className="me-2" animation="border" />}
          End session
        </Button>
      ) : (
        // Fallback only — the session auto-starts; this lets the participant retry after an error.
        <Button variant="light" size="lg" onClick={start}>
          Reconnect
        </Button>
      )}

      <p className="small mb-0" style={{ maxWidth: 360, opacity: 0.6 }}>
        The assistant is your only interface here. Once the microphone is allowed, just speak
        and tell it what you would like to do.
      </p>
    </div>
  )
}

/** Simple status orb: grey when idle, pulsing green when live, red on error. */
function Orb({ status }: { status: VoiceStatus }) {
  const color =
    status === 'live' ? '#2ecc71' : status === 'error' ? '#e74c3c' : status === 'connecting' ? '#f1c40f' : '#415a77'

  return (
    <>
      <style>
        {`@keyframes voicePulse {
            0% { box-shadow: 0 0 0 0 rgba(46, 204, 113, 0.5); }
            70% { box-shadow: 0 0 0 28px rgba(46, 204, 113, 0); }
            100% { box-shadow: 0 0 0 0 rgba(46, 204, 113, 0); }
          }`}
      </style>
      <div
        aria-hidden
        style={{
          width: 96,
          height: 96,
          borderRadius: '50%',
          background: color,
          transition: 'background 0.3s ease',
          animation: status === 'live' ? 'voicePulse 1.8s infinite' : undefined,
        }}
      />
    </>
  )
}
