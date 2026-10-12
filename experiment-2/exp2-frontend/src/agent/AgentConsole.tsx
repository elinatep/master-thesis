import { useCallback, useEffect, useState } from 'react'
import { Alert, Badge, Button, Card, Container, Spinner, Table } from 'react-bootstrap'
import ConditionCard from './ConditionCard'
import { callTokenUrl, endCall, fetchBrief, fetchQueue, pickUp, type Brief, type QueueEntry } from './agentApi'
import { useAgentCall } from '../call/useAgentCall'

/**
 * The human agent's console: who is waiting, and the one call they are on.
 *
 * Behind the agent credential. The page is served by the SPA fallback, so opening /agent makes the
 * browser ask for it (HTTP Basic) before this renders at all, and the fetches below inherit it.
 *
 * Read mid-call by someone talking to a participant, so it is built to be glanced at rather than
 * studied: the instruction is the largest thing on the screen, the rule they must not break is
 * directly beneath it, and the transcript they have to open the call from is one scroll away.
 */

const QUEUE_POLL_MS = 3000

export default function AgentConsole() {
  const [queue, setQueue] = useState<QueueEntry[] | null>(null)
  const [brief, setBrief] = useState<Brief | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  const refreshQueue = useCallback(async () => {
    try {
      setQueue(await fetchQueue())
      setError(null)
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e))
    }
  }, [])

  // Poll only while nobody is on a call. During a call the queue is irrelevant and a background
  // fetch every three seconds is one more thing that can throw while someone is talking.
  useEffect(() => {
    if (brief) return
    void refreshQueue()
    const interval = window.setInterval(() => void refreshQueue(), QUEUE_POLL_MS)
    return () => window.clearInterval(interval)
  }, [brief, refreshQueue])

  async function take(handoverId: number) {
    setBusy(true)
    setError(null)
    try {
      setBrief(await pickUp(handoverId))
    } catch (e) {
      const status = (e as { status?: number }).status
      if (status === 409) {
        // Normal, not a failure: somebody else got there first, or this one is already over.
        setError('Someone else has already taken that one.')
        await refreshQueue()
      } else {
        setError(e instanceof Error ? e.message : String(e))
      }
    } finally {
      setBusy(false)
    }
  }

  if (brief) {
    return (
      <CallScreen
        brief={brief}
        onRefresh={async () => setBrief(await fetchBrief(brief.handoverId))}
        onFinished={async () => {
          setBrief(null)
          await refreshQueue()
        }}
      />
    )
  }

  return (
    <Container className="py-4" style={{ maxWidth: 900 }}>
      <h1 className="h4 mb-1">Customer service — waiting calls</h1>
      <p className="text-muted">
        Read <code>configuration/human/human-base.md</code> and your condition block before your
        first session. The instruction for each call is on the card once you pick it up.
      </p>

      {error && <Alert variant="danger">{error}</Alert>}

      {queue === null ? (
        <Spinner animation="border" />
      ) : queue.length === 0 ? (
        <Card body className="text-muted">
          Nobody is waiting. This updates on its own.
        </Card>
      ) : (
        <Table hover>
          <thead>
            <tr>
              <th>Participant</th>
              <th>Waiting</th>
              <th />
            </tr>
          </thead>
          <tbody>
            {queue.map((entry) => (
              <tr key={entry.handoverId}>
                <td className="font-monospace">{entry.participantId}</td>
                <td>{formatWait(entry.waitedSeconds)}</td>
                <td className="text-end">
                  <Button size="sm" disabled={busy} onClick={() => void take(entry.handoverId)}>
                    Pick up
                  </Button>
                </td>
              </tr>
            ))}
          </tbody>
        </Table>
      )}
    </Container>
  )
}

/**
 * One call. The agent is on the phone from the moment they press Join, so everything they need is
 * on this screen and nothing takes them off it.
 */
function CallScreen({
  brief,
  onRefresh,
  onFinished,
}: {
  brief: Brief
  onRefresh: () => Promise<void>
  onFinished: () => Promise<void>
}) {
  const { status, error, muted, join, hangUp, toggleMute } = useAgentCall({
    tokenUrl: callTokenUrl(brief.handoverId),
    // What the participant's client would show if it displayed names. Kept generic rather than the
    // agent's own name: the name they give on the call comes off their condition card, and two
    // sources for it is two things to keep in step.
    displayName: 'Salvena customer service',
  })
  const [ending, setEnding] = useState(false)

  async function finish() {
    setEnding(true)
    try {
      await hangUp()
      await endCall(brief.handoverId)
      await onFinished()
    } finally {
      setEnding(false)
    }
  }

  return (
    <Container className="py-4" style={{ maxWidth: 900 }}>
      <div className="d-flex justify-content-between align-items-start mb-3">
        <div>
          <h1 className="h4 mb-0">
            Call with <span className="font-monospace">{brief.participantId}</span>
          </h1>
          <div className="text-muted small">
            handover {brief.handoverId} · waited {formatWait(brief.waitedSeconds)}
            {brief.agentId && ` · picked up by ${brief.agentId}`}
          </div>
        </div>
        <Badge
          bg={status === 'live' ? 'success' : status === 'error' ? 'danger' : 'secondary'}
          className="fs-6 px-3 py-2"
        >
          {status === 'live' ? 'on the call' : status === 'waiting' ? 'waiting for them' : status}
        </Badge>
      </div>

      <ConditionCard humanAck={brief.humanAck} />

      <Card className="mb-3">
        <Card.Body>
          <div className="d-flex gap-2 align-items-center">
            {status === 'idle' || status === 'error' ? (
              <Button size="lg" onClick={() => void join()}>
                Join the call
              </Button>
            ) : (
              <>
                <Button variant={muted ? 'warning' : 'outline-secondary'} onClick={() => void toggleMute()}>
                  {muted ? 'Unmute' : 'Mute'}
                </Button>
                <Button variant="danger" disabled={ending} onClick={() => void finish()}>
                  {ending ? 'Ending…' : 'End call'}
                </Button>
              </>
            )}
            {status === 'ended' && (
              <Button variant="outline-secondary" disabled={ending} onClick={() => void finish()}>
                Back to the queue
              </Button>
            )}
            <Button variant="link" className="ms-auto" onClick={() => void onRefresh()}>
              Refresh
            </Button>
          </div>
          {status === 'waiting' && (
            <div className="text-muted small mt-2">
              You are in the call and they are not here yet. They may still be finishing with the
              assistant.
            </div>
          )}
          {error && <div className="text-danger mt-2">{error}</div>}
        </Card.Body>
      </Card>

      <Card className="mb-3">
        <Card.Body>
          <div className="text-uppercase small text-muted mb-2">
            What the assistant handed over
          </div>
          {brief.summary && Object.keys(brief.summary).length > 0 ? (
            <Table size="sm" className="mb-0">
              <tbody>
                {Object.entries(brief.summary).map(([key, value]) => (
                  <tr key={key}>
                    <td className="text-muted" style={{ width: '12rem' }}>
                      {key.replace(/_/g, ' ')}
                    </td>
                    <td>{typeof value === 'string' ? value : JSON.stringify(value)}</td>
                  </tr>
                ))}
              </tbody>
            </Table>
          ) : (
            <div className="text-muted">
              Nothing — read the transcript below instead and open on a detail from it.
            </div>
          )}
        </Card.Body>
      </Card>

      <Card>
        <Card.Body>
          <div className="text-uppercase small text-muted mb-2">
            What they said to the assistant
          </div>
          <Alert variant="light" className="py-2 small">
            Open by naming yourself, saying you have read this, and referring to{' '}
            <strong>something specific from it</strong>. Never ask them to explain the claim again.
            That is the same in every condition.
          </Alert>
          {brief.transcript.length === 0 ? (
            <div className="text-muted">
              No transcript yet. If it is empty mid-call, the assistant's leg may have failed — flag
              the session afterwards.
            </div>
          ) : (
            <div style={{ maxHeight: '22rem', overflowY: 'auto' }}>
              {brief.transcript.map((line, i) => (
                <div key={i} className="mb-2">
                  <span className={`badge me-2 ${line.role === 'USER' ? 'bg-primary' : 'bg-secondary'}`}>
                    {line.role === 'USER' ? 'them' : 'assistant'}
                  </span>
                  {line.text}
                </div>
              ))}
            </div>
          )}
        </Card.Body>
      </Card>
    </Container>
  )
}

function formatWait(seconds: number): string {
  if (seconds < 60) return `${seconds}s`
  const m = Math.floor(seconds / 60)
  return `${m}m ${seconds % 60}s`
}
