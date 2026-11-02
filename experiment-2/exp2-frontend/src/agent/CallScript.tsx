import { Badge, Card } from 'react-bootstrap'

/**
 * The words, on the screen the agent is already looking at.
 *
 * The scripts live in configuration/human/, but reading them means a second window next to a live
 * call - and the thing that gets dropped when you are mid-sentence with an upset participant is
 * whichever one is not in front of you.
 *
 * The structure is the design: the opening, the three questions and the close are one source,
 * rendered for both conditions and marked as shared, and only the three responses branch. Keeping
 * the questions in one place is not tidiness - a question worded differently between the arms is a
 * second manipulation nobody recorded, and an emotion word in one of them ("what concerned you"
 * against "what you wanted reviewed") is exactly how that arrives.
 */
export default function CallScript({ humanAck }: { humanAck: boolean }) {
  // The only thing that differs between conditions: what you say before each question.
  const responses = humanAck
    ? {
        kind: 'Acknowledge',
        first:
          "Yeah, I can definitely see why this would be frustrating, especially since you were expecting around ninety per cent of the costs to be covered.",
        second:
          "Yeah, I understand. I think it's completely reasonable to want an explanation for such a big difference.",
        summary:
          "Okay, thank you. I'm sorry you've had to deal with this, especially after the water damage itself. I've noted everything you've told me, and I have all the information I need now.",
      }
    : {
        kind: 'Respond to the content',
        first:
          'Okay, so your policy indicated ninety per cent coverage, but the amount offered was significantly lower.',
        second:
          "Okay, I'll make a note that you're disputing the difference between the expected coverage and the amount offered.",
        summary:
          "Okay, thank you. I've noted everything you've told me, including the difference in coverage and the explanation you received. I have all the information I need now.",
      }

  return (
    <Card className="mb-3">
      <Card.Body>
        <div className="text-uppercase small text-muted mb-2">How the call runs</div>
        <p className="small">
          The opening, the three questions and the close are <Same /> — ask them in the same words
          whichever condition you are running, skip one only if they have already answered it, and
          never add a question this arm would not ask. <strong>Never ask about feelings</strong>,
          in either arm: that is a second manipulation, not this one.
        </p>

        <Step n={1} title="Open" shared>
          <Line>
            Hi, my name is ___. I've just read through your conversation with our AI assistant, so
            I'm already familiar with your case. I can see that you submitted a claim for twelve
            hundred pounds following water damage, but you were only offered sixty. Could you tell
            me what you'd like us to look into?
          </Line>
        </Step>

        <Step n={2} title="Let them explain">
          <p className="mb-0">Do not interrupt.</p>
        </Step>

        <Step n={3} title={`Respond — ${responses.kind}`}>
          <Line>{responses.first}</Line>
          <Ask>
            Was it mainly the amount itself that you wanted reviewed, or also the explanation you
            received?
          </Ask>
        </Step>

        <Step n={4} title={`Respond — ${responses.kind}`}>
          <Line>{responses.second}</Line>
          <Ask>Was there anything in the assessment that explained why the amount was reduced?</Ask>
          <p className="mb-0 mt-1 small text-muted">
            Then listen. They may say no, or that only part of the damage was covered.
          </p>
        </Step>

        <Step n={5} title="Ask" shared>
          <Line>
            Okay, thanks for explaining. Is there anything else about the claim that you'd like me
            to include in the review?
          </Line>
        </Step>

        <Step n={6} title={`Summarise — ${responses.kind}`}>
          <Line>{responses.summary}</Line>
        </Step>

        <Step n={7} title="Close — near enough word for word" shared>
          <Line>
            Okay, I think that's everything I need from you. The review is now complete on my side,
            and you'll see the final outcome at the end of this session. Thank you for your time,
            and have a nice day. Goodbye!
          </Line>
          <p className="mb-0 mt-1 small">End the call. Add nothing after it, however they respond.</p>
        </Step>

        {humanAck ? (
          <p className="small mb-0">
            <strong>At least two acknowledgements across the call</strong> — take the slots where
            there is actually something to respond to. Name the specific thing, say the reaction is
            reasonable. <strong>Acknowledge, then ask — never acknowledge and stop:</strong> it goes
            in front of the question, in the same turn. Not agreeing, not apologising for the
            outcome, not therapy. If they are cheerful, do not manufacture a feeling — acknowledge
            the nuisance instead.
          </p>
        ) : (
          <p className="small mb-0">
            <strong>Business-like, not cold.</strong> "Okay", "Right", "Thanks for explaining" are
            fine — use them, and ask real questions about the claim. Silence is not the condition.
            When they express feeling, answer the content and carry on to the question: no "I
            understand how you feel", no "that must be frustrating", no "I'm sorry you've had to
            deal with this". Do not rebuke it either. Same questions as the other arm, so the same
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

/** A question, which is shared by construction - there is one copy of each. */
function Ask({ children }: { children: React.ReactNode }) {
  return (
    <div className="mt-2">
      <div className="small fw-semibold">
        Then ask <Same />
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
