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
          "Oh no, I'm really sorry — that sounds so frustrating, especially on top of the burst pipe itself. Let me take this down as a dispute and go through the assessment with you.",
        second:
          "Oh, I see. Yeah, that's a huge difference, and getting a decision like that with nothing explaining it must be really disappointing. I'll make a note of that.",
        summary:
          "Of course, that makes complete sense. I know this isn't how you wanted to spend your afternoon. I've got everything I need now.",
      }
    : {
        kind: 'Respond to the content',
        first:
          "Okay. Let me take this down as a dispute and go through the assessment with you.",
        second: "Okay, so nothing explaining it. I'll make a note of that.",
        summary:
          "Right, thank you. I've got everything I need now.",
      }

  return (
    <Card className="mb-3">
      <Card.Body>
        <div className="text-uppercase small text-muted mb-2">How the call runs</div>
        <p className="small">
          The opening, both questions, the finding and the close are <Same /> — say them the same
          way whichever condition you are running, and never add a question this arm would not ask.{' '}
          <strong>Never ask about feelings</strong>, in either arm: that is a second manipulation,
          not this one. <strong>Never ask for photos, invoices or repair estimates</strong>, or
          suggest damage was left out — the assessment told them their documentation was complete
          and the damage is not disputed.
        </p>

        <Step n={1} title="Open" shared>
          <Line>
            Hi! My name is ___, and I'll be helping you today. I've just gone through your
            conversation with our AI assistant, so I'm already familiar with your claim. I can see
            you've had some water damage at home, and you're calling about the amount you've been
            offered. Is that right?
          </Line>
        </Step>

        <Step n={2} title="Let them explain">
          <p className="mb-0">They say yes, then make their complaint. Do not interrupt.</p>
        </Step>

        <Step n={3} title={`Your response — ${responses.kind}`}>
          <Line>{responses.first}</Line>
        </Step>

        <Step n={4} title="First question" shared>
          <Line>
            Let me have a closer look at the assessment with you, okay? Did they give you any
            explanation for how they calculated the amount?
          </Line>
          <p className="mb-0 mt-1 small text-muted">
            They will say no. That is the <strong>correct</strong> answer — the assessment screen
            says no further explanation is provided — and recording it is the point of asking.
          </p>
        </Step>

        <Step n={5} title={`Your response — ${responses.kind}`}>
          <Line>{responses.second}</Line>
        </Step>

        <Step n={6} title="Second question" shared>
          <Line>
            And just so I've got it right for the review — when you filed the claim, what did the
            portal tell you your cover would be?
          </Line>
          <p className="mb-0 mt-1 small text-muted">
            They will say ninety per cent. They saw it on the form.
          </p>
        </Step>

        <Step n={7} title={`Your response — ${responses.kind}`}>
          <Line>{responses.summary}</Line>
        </Step>

        <Step n={8} title="The finding" shared>
          <Line>
            Okay, so what I'm recording is the gap between the ninety per cent you were shown when
            you filed and the five per cent that was actually applied, with no breakdown given. I've
            made sure your concerns about the assessment are in the review.
          </Line>
        </Step>

        <Step n={9} title="Close — near enough word for word" shared>
          <Line>
            Thank you so much for taking the time to speak with me today! The review is complete on
            my side, and you'll be able to see the final outcome at the end of this session. Take
            care, and goodbye!
          </Line>
          <p className="mb-0 mt-1 small">End the call. Add nothing after it, however they respond.</p>
        </Step>

        {humanAck ? (
          <p className="small mb-0">
            <strong>Each line is the other arm's line with the acknowledgement in front of it</strong>
            — the second half of each is word for word what H− says, so the only thing this
            condition adds is the response to how they feel. All three slots, every call where
            there is something to respond to; the floor is two. Name the specific thing, say the
            reaction is reasonable, and let it sound like speech. Not agreeing, not apologising for
            the outcome, not therapy. If they are cheerful, do not manufacture a feeling —
            acknowledge the nuisance instead.
          </p>
        ) : (
          <p className="small mb-0">
            <strong>These are also the second half of every line in H+, word for word.</strong> That
            arm says all of this and puts an acknowledgement in front of it; you say only this, so
            adding your own warmth here closes the gap being measured.{' '}
            <strong>Do not hand their account back to them</strong> — "so just to recap, the claim
            went in at twelve hundred and sixty came back" names no feeling and still makes someone
            feel heard. Say what you are doing with the file, not what they just told you. Friendly,
            not flat: "okay", "right", a warm voice. Name no feeling, and do not rebuke one either.
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

function Pushback({ q, children }: { q: string; children: React.ReactNode }) {
  return (
    <div className="mb-2">
      <div className="small fw-semibold">{q}</div>
      <Line>{children}</Line>
    </div>
  )
}
