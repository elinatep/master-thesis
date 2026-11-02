import { Card } from 'react-bootstrap'

/**
 * The words, on the screen the agent is already looking at.
 *
 * The scripts live in configuration/human/, but reading them means a second window next to a live
 * call - and the thing that gets dropped when you are mid-sentence with an upset participant is
 * whichever one is not in front of you. The standardised lines matter most here: the close and the
 * pushback answers are the two places improvising is a difference between participants that nobody
 * recorded.
 *
 * Only step 3 varies. Everything else renders identically in both conditions, from the same source,
 * so the two cannot drift apart as the files are edited.
 */
export default function CallScript({ humanAck }: { humanAck: boolean }) {
  return (
    <Card className="mb-3">
      <Card.Body>
        <div className="text-uppercase small text-muted mb-2">How the call runs</div>

        <Step n={1} title="Open — the same in both conditions">
          <Line>
            Hi, my name is ___. I've just read through your conversation with our AI assistant, so
            I'm already familiar with your case. I can see that you submitted a claim for twelve
            hundred pounds following water damage, but the amount you were offered was only sixty.
            Could you tell me what you'd like us to look into?
          </Line>
          <p className="mb-0 mt-1 small">
            Name yourself · say you have read the conversation · refer to a detail they gave. Never
            ask them to start again.
          </p>
        </Step>

        <Step n={2} title="Let them say what they want to say">
          <p className="mb-0">
            Do not interrupt. Do not ask them to repeat themselves — but let them if they want to.
          </p>
        </Step>

        {humanAck ? (
          <Step n={3} title="ACKNOWLEDGE — at least twice, where the moment is there">
            <p className="mb-1 text-muted small">Early, once they have said what is wrong:</p>
            <Line>
              Yeah, I can definitely see why this would be frustrating, especially since you were
              expecting around ninety per cent of the costs to be covered.
            </Line>
            <p className="mb-1 mt-2 text-muted small">In the middle, when they make their case:</p>
            <Line>
              I understand. I think it's completely reasonable that you'd want an explanation,
              especially given the difference between what you expected and what you actually
              received.
            </Line>
            <p className="mb-1 mt-2 text-muted small">Towards the end:</p>
            <Line>
              I'm sorry you've had to deal with this. I can imagine it's been quite stressful,
              especially on top of the water damage itself.
            </Line>
            <p className="mb-0 mt-2 small">
              Name the feeling and connect it to <em>their</em> situation. Say the reaction is
              reasonable. One or two sentences, then move on —{' '}
              <strong>instead of</strong> procedural detail, not on top of it. Not at fixed points:
              wait until they have said something with feeling in it. Not agreeing, not apologising
              for the outcome, not therapy. If they are cheerful, do not manufacture a feeling —
              acknowledge the nuisance instead.
            </p>
          </Step>
        ) : (
          <Step n={3} title="FACTUAL — the same things, at the same length">
            <p className="mb-1 text-muted small">Early, once they have said what is wrong:</p>
            <Line>
              Okay, so I can see that your policy indicated ninety per cent coverage, but the amount
              you received was significantly lower. I'll go through the details of your claim and
              make sure everything is recorded correctly.
            </Line>
            <p className="mb-1 mt-2 text-muted small">In the middle, when they make their case:</p>
            <Line>
              Okay, thanks for explaining. I'll include that information in the case notes, so that
              it's taken into account as part of the review.
            </Line>
            <p className="mb-1 mt-2 text-muted small">Towards the end:</p>
            <Line>
              Alright, I have all the information I need now. The details have been recorded, and
              there's nothing else you need to provide at this stage.
            </Line>
            <p className="mb-0 mt-2 small">
              "Okay", "Right", "Thanks for explaining" are fine — use them, and ask real questions
              about the claim. <strong>Business-like, not cold.</strong> When they express feeling,
              answer the content, never the feeling: no "I understand how you feel", no "that must
              be frustrating", no "I'm sorry you've had to deal with this". Do not rebuke it either,
              and do not go quiet — if everything left to say would be acknowledgement, go back to
              the detail of the claim.
            </p>
          </Step>
        )}

        <Step n={4} title="Confirm what you have — the same in both conditions">
          <Line>
            So the point you're disputing is the ninety per cent cover against the sixty that came
            back, and you'd like the assessment looked at again. I've got that recorded.
          </Line>
        </Step>

        <Step n={5} title="Close — near enough word for word">
          <Line>
            Okay, I think that's everything I need from you. The review is now complete on my side,
            and you'll see the final outcome at the end of this session. Thank you for your time,
            and have a nice day. Goodbye!
          </Line>
          <p className="mb-0 mt-1 small">End the call. Add nothing after it, however they respond.</p>
        </Step>

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

function Step({ n, title, children }: { n: number; title: string; children: React.ReactNode }) {
  return (
    <div className="mb-3">
      <div className="fw-semibold">
        {n}. {title}
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
