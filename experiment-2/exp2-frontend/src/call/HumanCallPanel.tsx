import { useState } from 'react'
import { Badge, Button, Card, Spinner } from 'react-bootstrap'
import { useAgentCall } from './useAgentCall'
import { useLiveTranscribe } from './useLiveTranscribe'
import { useHandback } from '../study/useHandback'
import { participantHeaders } from '../study/headers'
import { logTranscript } from '../study/log'
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
    // Without this the server has no idea whose call to issue a token for. It answers 400, the
    // participant never joins, and they sit watching "connecting" while the agent waits in an
    // empty call - which is exactly what happened on the first live run.
    tokenHeaders: participantHeaders,
    // Not a study variable: the participant's name is already known to the agent from the brief,
    // and the agent's own name comes off their condition card, not from here.
    displayName: 'Customer',
    autoJoin: true,
  })
  // The call ending is the finish; asking them to confirm it reads as the page not knowing.
  const { finishing, finish } = useHandback({ confirm: false })
  const [confirmingLeave, setConfirmingLeave] = useState(false)

  // Off unless the configuration turns it on - their audio on this leg is a decision for the
  // ethics application, not a default. The server decides; the page does not get a vote.
  useLiveTranscribe({
    active: status === 'live',
    tokenUrl: '/api/call/transcribe/token',
    tokenHeaders: participantHeaders,
    onLine: (text) => logTranscript('USER', text),
  })

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
            {/*
              Says what the pause IS. The assistant has just promised a colleague and then gone
              quiet, and unexplained silence after a promise reads as having been dropped - in a
              study about whether people feel heard, that would be a confound the implementation
              introduced. Naming the wait as a person reading their case makes it legible, and
              says the thing nothing else on this page says: that it is a person.

              Factual, not reassuring. "Sorry for the wait" would be the service acknowledging
              them, which is the manipulation, in the one place it must be identical in all four
              cells.
            */}
            <span>
              {status === 'connecting'
                ? 'Connecting you…'
                : 'A customer service employee is reading your case and will be with you shortly. '
                  + 'Please stay on the line.'}
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
          <>
            <div className="d-flex gap-2 mt-3">
              <Button variant={muted ? 'warning' : 'outline-secondary'} onClick={() => void toggleMute()}>
                {muted ? 'Unmute' : 'Mute'}
              </Button>
            </div>
            {/*
              They can always leave, and nothing here could stop them: the tab close, the back
              button and the window X are always available, and a participant who uses one of those
              is lost silently - no event, no handback, a Prolific submission to adjudicate by hand.
              Keeping a way out is also a consent condition, not a courtesy.

              What it should not be is an invitation. It used to sit beside Mute, styled like an
              ordinary call control, which is the one thing it must not look like: the agent runs
              the standardised close, so this is the exception. A quiet link, and a confirm, so
              leaving is deliberate rather than the obvious thing to click.
            */}
            <div className="mt-3 text-end">
              {confirmingLeave ? (
                <>
                  <span className="small text-muted me-2">Leave the call?</span>
                  <Button size="sm" variant="outline-secondary" className="me-2"
                      onClick={() => setConfirmingLeave(false)}>
                    Stay
                  </Button>
                  <Button size="sm" variant="outline-danger" onClick={() => void hangUp()}>
                    Yes, leave
                  </Button>
                </>
              ) : (
                <Button variant="link" size="sm" className="text-muted p-0"
                    onClick={() => setConfirmingLeave(true)}>
                  I need to stop the call
                </Button>
              )}
            </div>
          </>
        )}
      </Card.Body>
    </Card>
  )
}
