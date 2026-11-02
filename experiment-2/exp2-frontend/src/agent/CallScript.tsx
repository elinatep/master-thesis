import { Badge, Card } from 'react-bootstrap'

/**
 * The words, on the screen the agent is already looking at.
 *
 * The scripts live in configuration/human/, but reading them means a second window next to a live
 * call - and the thing that gets dropped when you are mid-sentence with an upset participant is
 * whichever one is not in front of you.
 *
 * The structure is the design: the opening, the bridge, the two checks, the finding and the close
 * are one source, rendered for both conditions and marked as shared, and only the three responses
 * branch. Keeping the shared lines in one place is not tidiness - a question worded differently
 * between the arms is a second manipulation nobody recorded, and an emotion word in one of them
 * ("what concerned you" against "what you wanted reviewed") is exactly how that arrives.
 */
export default function CallScript({ humanAck }: { humanAck: boolean }) {
  // The only thing that differs between conditions: what you say before each question.
  const responses = humanAck
    ? {
        kind: 'Acknowledge',
        first:
          "Yeah, I can definitely see why that's frustrating. It's a big difference, especially when you've already had the water damage to deal with.",
        second:
          "I understand. I think it's completely reasonable to want an explanation for a difference that size.",
        summary:
          "Okay, thank you. I'm sorry you've had to chase this, especially after the water damage itself. I've got it all recorded.",
      }
    : {
        kind: 'Respond to the content',
        first:
          "Okay. So that's ninety per cent indicated when you filed, against the sixty that came back.",
        second: 'Okay, thank you for confirming those.',
        summary:
          "Okay, thank you. I've got that recorded — the ninety per cent indicated at filing, the sixty awarded, and no breakdown provided.",
      }

  return (
    <Card className="mb-3">
      <Card.Body>
        <div className="text-uppercase small text-muted mb-2">How the call runs</div>
        <p className="small">
          The opening, the two checks, the finding and the close are <Same /> — say them the same
          way whichever condition you are running, and never add a question this arm would not ask.{' '}
          <strong>Never ask about feelings</strong>, in either arm: that is a second manipulation,
          not this one. <strong>Never ask for photos, invoices or repair estimates</strong>, or
          suggest damage was left out — the assessment told them their documentation was complete
          and the damage is not disputed.
        </p>

        <Step n={1} title="Open" shared>
          <Line>
            Hi, this is ___ from Salvena. I've received your case from our AI assistant and I've had
            a look at your claim. I understand you'd like to dispute the amount you've been offered
            — is that right?
          </Line>
        </Step>

        <Step n={2} title="Let them explain">
          <p className="mb-0">They say yes, then make their complaint. Do not interrupt.</p>
        </Step>

        <Step n={3} title={`Respond — ${responses.kind}`}>
          <Line>{responses.first}</Line>
          <Ask label="Then">
            Let me check two things on the file so I can get the review recorded properly.
          </Ask>
        </Step>

        <Step n={4} title="First check" shared>
          <Line>When you filed the claim, what did the portal indicate your cover would be?</Line>
          <p className="mb-0 mt-1 small text-muted">
            They will say ninety per cent — they saw it on the form.
          </p>
        </Step>

        <Step n={5} title="Second check" shared>
          <Line>And did the assessment give you any breakdown of how it arrived at sixty?</Line>
          <p className="mb-0 mt-1 small text-muted">
            They will say no. That is the <strong>correct</strong> answer — the assessment screen
            says no further explanation is provided — and recording it is the point of asking.
          </p>
        </Step>

        <Step n={6} title={`Respond — ${responses.kind}`}>
          <Line>{responses.second}</Line>
          <Ask label="Then the finding">
            So what I'm recording is the discrepancy between the ninety per cent indicated at the
            point of filing and the five per cent that was applied, with no breakdown given.
          </Ask>
          <p className="mb-0 mt-1 small text-muted">
            This is the one thing the call produces that nothing before it did. The assistant took
            the claim; you established what is actually wrong with the assessment.
          </p>
        </Step>

        <Step n={7} title={`Summarise — ${responses.kind}`}>
          <Line>{responses.summary}</Line>
          <p className="mb-0 mt-1 small text-muted">
            Do not end on "that's everything I need from you" — the close says it.
          </p>
        </Step>

        <Step n={8} title="Close — near enough word for word" shared>
          <Line>
            Okay, I think that's everything I need from you. The review is now complete on my side,
            and you'll see the final outcome at the end of this session. Thank you for your time,
            and have a nice day. Goodbye!
          </Line>
          <p className="mb-0 mt-1 small">End the call. Add nothing after it, however they respond.</p>
        </Step>

        {humanAck ? (
          <p className="small mb-0">
            <strong>Three slots — steps 3, 6 and 7 — and the floor is two.</strong> Take the ones
            where there is actually something to respond to. Name the specific thing, say the
            reaction is reasonable.{' '}
            <strong>Acknowledge, then carry on — never acknowledge and stop:</strong> it goes in
            front of the bridge or the finding, in the same turn. Not agreeing, not apologising for
            the outcome, not therapy. If they are cheerful, do not manufacture a feeling —
            acknowledge the nuisance instead.
          </p>
        ) : (
          <p className="small mb-0">
            <strong>Business-like, not cold.</strong> "Okay", "Right", "Thanks for explaining" are
            fine — use them, and ask real questions about the claim. Silence is not the condition.
            When they express feeling, answer the content and carry on to the question: no "I
            understand how you feel", no "that must be frustrating", no "I'm sorry you've had to
            chase this". Do not rebuke it either. Same two checks as the other arm, so the same
            floor — but give responses of the same <em>length</em>, or the call ends sooner and
            length becomes part of the manipulation.
          </p>
        )}

        <details className="mt-3">
          <summary className="fw-semibold" style={{ cursor: 'pointer' }}>
            Pushback — use these answers, do not improvise
          </summary>
          <div className="mt-2">
            <Pushback q="How much am I getting?">
              The final amount will be shown to you at the end of the session. Unfortunately, I
              can't confirm it during the call.
            </Pushback>
            <Pushback q="Can you change the amount?">
              I can't change the amount directly, but I can make sure your dispute is properly
              recorded.
            </Pushback>
            <Pushback q="Why couldn't the AI help me?">
              The AI assistant collects the initial information, but disputed claims are passed on
              to a human agent.
            </Pushback>
            <Pushback q="Are you a real person?">
              Yes, I'm a real person. I'm helping with the customer service part of this research
              study.
            </Pushback>
            <p className="small mb-2">
              <strong>Never promise what happens next.</strong> Say what <em>you</em> are doing, not
              what the assessment team will do with it — that is a claim about a process you do not
              control, and the kind of sentence that gets said in one condition and not the other.
            </p>
            <p className="small mb-0">
              <strong>Abusive, or distressed beyond the scenario</strong> — drop the script and be a
              person. Say they can stop at any point and that nothing depends on finishing. End the
              call if that is the right thing, and flag the session afterwards so it can be
              excluded. A lost data point is a much smaller problem than the alternative.
            </p>
          </div>
        </details>
      </Card.Body>
    </Card>
  )
}

/** Marks a line that must be said the same way in both conditions. */
function Same() {
  return (
    <Badge bg="light" text="dark" className="border align-text-bottom">
      same in both
    </Badge>
  )
}

function Step({
  n,
  title,
  shared,
  children,
}: {
  n: number
  title: string
  shared?: boolean
  children: React.ReactNode
}) {
  return (
    <div className="mb-3">
      <div className="fw-semibold">
        {n}. {title} {shared && <Same />}
      </div>
      {children}
    </div>
  )
}

/** A line to say. Quoted and set apart so it reads as speech rather than instruction. */
function Line({ children }: { children: React.ReactNode }) {
  return (
    <blockquote className="border-start border-3 ps-3 mb-0 fst-italic">"{children}"</blockquote>
  )
}

/** A shared line that follows a condition response, in the same turn: the bridge, or the finding. */
function Ask({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div className="mt-2">
      <div className="small fw-semibold">
        {label} <Same />
      </div>
      <Line>{children}</Line>
    </div>
  )
}

function Pushback({ q, children }: { q: string; children: React.ReactNode }) {
  return (
    <div className="mb-2">
      <div className="small fw-semibold">{q}</div>
      <Line>{children}</Line>
    </div>
  )
}
