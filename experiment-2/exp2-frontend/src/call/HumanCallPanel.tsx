import { Badge, Button, Card, Spinner } from 'react-bootstrap'
import { useAgentCall } from './useAgentCall'
import { useHandback } from '../study/useHandback'
import { VOICE_WIDGET_GAP } from '../study/layout'

const WIDTH = `min(420px, calc(100vw - ${2 * VOICE_WIDGET_GAP}px))`
const RIGHT = VOICE_WIDGET_GAP

/**
 * The participant's side of the call with the human employee.
 *
 * Deliberately plain. Everything the study varies happens in what the agent says, so this panel
 * has to be identical in all four cells and has to add as little of its own character as possible:
 * no reassurance, no apology for the wait, nothing that could itself be read as the service
 * acknowledging them.
 *
 * What it does owe them is honesty about state. The assistant has just gone quiet after promising a
 * colleague, and silence after a promise reads as having been dropped - which, in a study measuring
 * whether people feel heard, would be a confound introduced by the implementation.
 */
export default function HumanCallPanel() {
  const { status, error, muted, hangUp, toggleMute } = useAgentCall({
    tokenUrl: '/api/call/token',
    // Not a study variable: the participant's name is already known to the agent from the brief,
    // and the agent's own name comes off their condition card, not from here.
    displayName: 'Customer',
    autoJoin: true,
  })
  // The call ending is the finish; asking them to confirm it reads as the page not knowing.
  const { finishing, finish } = useHandback({ confirm: false })

  return (
    <Card
      className="shadow-lg"
      style={{ position: 'fixed', right: RIGHT, bottom: VOICE_WIDGET_GAP, width: WIDTH, zIndex: 1050 }}
    >
      <Card.Body className="p-4">
        <div className="d-flex justify-content-between align-items-center">
          <strong className="fs-5">Customer service</strong>
          <Badge
            bg={status === 'live' ? 'success' : status === 'error' ? 'danger' : 'secondary'}
            className="fs-6 px-3 py-2"
          >
            {status === 'live' ? 'connected' : status === 'waiting' ? 'holding' : status}
          </Badge>
        </div>

        {(status === 'connecting' || status === 'waiting') && (
          <div className="d-flex align-items-center gap-3 mt-3">
            <Spinner animation="border" size="sm" />
            <span>
              {status === 'connecting'
                ? 'Connecting you…'
                : 'Putting you through to a colleague. Please stay on the line.'}
            </span>
          </div>
        )}

        {status === 'live' && (
          <p className="mt-3 mb-0">You are speaking with a Salvena customer-service employee.</p>
        )}

        {status === 'ended' && (
          <>
            <p className="mt-3">The call has ended.</p>
            <Button
              size="lg"
              className="w-100"
              disabled={finishing}
              onClick={() => void finish({ escalated: true })}
            >
              {finishing ? 'One moment…' : 'Continue to the questions'}
            </Button>
          </>
        )}

        {error && (
          <div className="text-danger mt-3">
            {error}
            {/*
              A refused microphone is the likeliest failure and the participant can fix it, so the
              line says what to do rather than only what went wrong.
            */}
            {error.includes('Microphone') && (
              <div className="small mt-1">
                Allow microphone access for this site in your browser, then reload the page.
              </div>
            )}
          </div>
        )}

        {(status === 'live' || status === 'waiting') && (
          <div className="d-flex gap-2 mt-3">
            <Button variant={muted ? 'warning' : 'outline-secondary'} onClick={() => void toggleMute()}>
              {muted ? 'Unmute' : 'Mute'}
            </Button>
            {/*
              They can always leave. The agent runs the standardised close, so this is not the
              normal way a call ends - but a participant who wants to stop has to be able to,
              without closing the tab and losing the session.
            */}
            <Button variant="outline-danger" className="ms-auto" onClick={() => void hangUp()}>
              Leave call
            </Button>
          </div>
        )}
      </Card.Body>
    </Card>
  )
}
