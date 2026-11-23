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
          "Oh wow, yeah, I can absolutely see why you're frustrated! I mean, you've already had to deal with a burst pipe and all the damage it caused, and now you're having to sort out the insurance as well. I'm really sorry you're having to deal with all of this.",
        second:
          "Oh, I see. Yeah, I can completely understand why you'd want that looked at again — especially when you were expecting most of the damage to be covered. That's a huge difference, and I can imagine it must be really disappointing to get a decision like that with nothing explaining it.",
        summary:
          "Of course, that makes complete sense. And I really appreciate you taking the time to explain everything — I know this probably isn't how you wanted to spend your afternoon, especially after everything else.",
      }
    : {
        kind: 'Respond to the content',
        first:
          "Okay, thanks for explaining that. So just so I've got the picture: the claim went in at twelve hundred for the water damage, and what's come back from the assessment is sixty. What I'll do is go through the assessment with you and get it all recorded properly for the review.",
        second:
          "Okay, so no breakdown was given. I'll put that down — that the amount came back without any explanation of how it was worked out. I'm recording it as part of the dispute, in your words rather than mine, so it goes in the way you've put it.",
        summary:
          "Right, ninety per cent — that matches what I've got on the file here. Thanks for confirming it. So that's both halves of it documented now, and I appreciate you taking the time to go through it with me.",
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
            <strong>All three slots, every call where there is something to respond to — the floor
            is two.</strong> Name the specific thing, say the reaction is reasonable, and let it
            sound like speech: "oh wow", "yeah", "I mean". Six of fourteen pilot participants did
            not believe they had spoken to a human, and that is the thing to fix.{' '}
            <strong>Acknowledge, then carry on — never acknowledge and stop.</strong> Not agreeing,
            not apologising for the outcome, not therapy. If they are cheerful, do not manufacture a
            feeling — acknowledge the nuisance instead.
          </p>
        ) : (
          <p className="small mb-0">
            <strong>Friendly, not flat.</strong> "Okay", "right", "thanks for explaining" — use
            them, with warmth in your voice. Six of fourteen pilot participants did not believe they
            had spoken to a human, and that applies here as much as in the other arm; business-like
            is not the same as cold. When they express feeling, answer the content and carry on: no
            "I understand how you feel", no "that must be frustrating", no "I'm sorry you're having
            to deal with this". Do not rebuke it either. These responses are written to take as long
            to say as the acknowledging ones — finishing much sooner means the study measured call
            length too.
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
