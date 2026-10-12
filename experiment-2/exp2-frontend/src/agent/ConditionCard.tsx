import { Alert, Card } from 'react-bootstrap'

/**
 * The agent's instruction for this call, and the case in front of them.
 *
 * Loud on purpose. This is read mid-call, by someone who may have run the opposite condition ten
 * minutes earlier, and running the wrong one is the single most expensive mistake available here -
 * it does not fail, it quietly produces a participant in a cell nobody assigned them to.
 *
 * It says nothing about the AI leg. The agent can read the transcript below and will often infer
 * it; what this card does is keep what they are *told* the same every session.
 */
export default function ConditionCard({ humanAck }: { humanAck: boolean | null }) {
  if (humanAck === null) {
    return (
      <Alert variant="danger">
        <Alert.Heading className="h6">No condition on this handover</Alert.Heading>
        Do not take this call. Tell the researcher - a session with no condition is not usable, and
        guessing makes it worse than losing it.
      </Alert>
    )
  }

  return (
    <>
      <Card className={`mb-3 border-3 ${humanAck ? 'border-success' : 'border-secondary'}`}>
        <Card.Body>
          <div className="text-uppercase small text-muted">Your instruction on this call</div>
          <div className="fs-3 fw-bold mt-1">
            {humanAck ? 'ACKNOWLEDGE how they feel' : 'DO NOT acknowledge how they feel'}
          </div>
          <div className="mt-2">
            {humanAck ? (
              <>
                Three acknowledging moments: early, in the middle when they make their case, and
                once before the close. Name the specific thing, say the reaction is reasonable.
                <span className="text-muted"> (human-ack.md)</span>
              </>
            ) : (
              <>
                Three procedural moments at the same points instead: what happens to a dispute, what
                you are recording, the steps from here. Courteous, business-like, never cold - and
                never name, reflect or rebuke what they are feeling.
                <span className="text-muted"> (human-noack.md)</span>
              </>
            )}
          </div>
        </Card.Body>
      </Card>

      <Alert variant="warning" className="py-2">
        <strong>Never say the final reimbursement</strong>, hint at it, or let it be inferred. The
        survey shows it afterwards. "I'm not able to give you the figure on the call — the outcome
        gets confirmed to you at the end of this session."
      </Alert>

      <Card className="mb-3">
        <Card.Body className="py-2">
          <div className="text-uppercase small text-muted mb-1">The case</div>
          <table className="table table-sm mb-0">
            <tbody>
              <tr>
                <td>Claim</td>
                <td className="fw-semibold">Water damage, burst pipe at home</td>
              </tr>
              <tr>
                <td>Amount claimed</td>
                <td className="fw-semibold">1,200</td>
              </tr>
              <tr>
                <td>Coverage indicated when they filed</td>
                <td className="fw-semibold">90%</td>
              </tr>
              <tr>
                <td>Assessment approved</td>
                <td className="fw-semibold">60</td>
              </tr>
            </tbody>
          </table>
          <div className="small text-muted mt-1">
            So they expected around 1,080 and were offered 60. Say "pounds", never "scenario
            pounds" — that is the study's word, not Salvena's.
          </div>
        </Card.Body>
      </Card>
    </>
  )
}
