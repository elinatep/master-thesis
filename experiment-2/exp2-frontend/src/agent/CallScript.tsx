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

        <Step n={1} title="Open — show you already read it">
          <p className="mb-1">
            Name yourself · say you have read the previous conversation · refer to one specific
            detail they gave. Never ask them to explain the claim again.
          </p>
          <Line>
            Hello, you're through to Salvena, this is ___. I've got your file in front of me and
            I've read through what you discussed a moment ago — the water damage from the burst
            pipe, the twelve hundred claimed, and the sixty that came back. Let me take it from
            here.
          </Line>
        </Step>

        <Step n={2} title="Let them say what they want to say">
          <p className="mb-0">
            Do not interrupt. Do not ask them to repeat themselves — but let them if they want to.
          </p>
        </Step>

        {humanAck ? (
          <Step n={3} title="ACKNOWLEDGE — three moments, your own words">
            <p className="mb-1 text-muted small">Early, before taking details:</p>
            <Line>
              I can hear this has been really frustrating — and honestly, I'm not surprised. You
              were told ninety per cent and what came back was sixty.
            </Line>
            <p className="mb-1 mt-2 text-muted small">In the middle, when they make their case:</p>
            <Line>
              That's the bit that would get me too — it's not that you didn't know what was covered,
              it's that you did, and it came back nothing like it.
            </Line>
            <p className="mb-1 mt-2 text-muted small">Once, before the close:</p>
            <Line>
              I know this hasn't been straightforward, and I'm sorry you've had to chase it.
            </Line>
            <p className="mb-0 mt-2 small">
              Name the <em>specific</em> thing, not "that's frustrating". Say the reaction is
              reasonable — "that would annoy anyone", not "I understand". Say these{' '}
              <strong>instead of</strong> procedural detail, not on top of it. Not agreeing, not
              apologising for the outcome, not therapy. If they are cheerful, do not manufacture a
              feeling: acknowledge the nuisance instead, and two moments is fine.
            </p>
          </Step>
        ) : (
          <Step n={3} title="PROCEDURAL DETAIL — three moments, your own words">
            <p className="mb-1 text-muted small">Early, before taking details:</p>
            <Line>
              So what I'll do is take this as a formal dispute on the assessment. That goes back to
              the assessment team with your account attached, and they look at the file again rather
              than just the summary — the policy wording, what was indicated on cover when you
              filed, and the breakdown they worked to.
            </Line>
            <p className="mb-1 mt-2 text-muted small">In the middle, when they make their case:</p>
            <Line>
              I'm recording that as the specific point of dispute — the cover indicated against what
              the assessment awarded. That's the bit they'll be asked to account for, so it's worth
              it being in your words rather than mine.
            </Line>
            <p className="mb-1 mt-2 text-muted small">Once, before the close:</p>
            <Line>
              That's the dispute logged and the file flagged for review. Nothing further is needed
              from you at this stage.
            </Line>
            <p className="mb-0 mt-2 small">
              When they express feeling — and they will — do not name it, reflect it or comment on
              it. No "I understand", no "that's frustrating". Carry on with the procedural point.
              This is <strong>business-like, not cold</strong>: polite, genuinely useful detail,
              real warmth in your voice. Finishing much faster than an acknowledging call means you
              are under-running it.
            </p>
          </Step>
        )}

        <Step n={4} title="Take the dispute">
          <Line>
            So — you're disputing the assessment because the policy indicated ninety per cent cover
            and the award came back at sixty. And what you're after is for that to be looked at
            again.
          </Line>
        </Step>

        <Step n={5} title="Close — near enough word for word">
          <Line>
            Right — I've got everything I need and the review is complete on my side. The final
            outcome will be shown to you at the end of this session rather than by me on the call.
            Thanks for taking the time to go through it. Goodbye.
          </Line>
          <p className="mb-0 mt-1 small">End the call. Add nothing after it, however they respond.</p>
        </Step>

        <details className="mt-3">
          <summary className="fw-semibold" style={{ cursor: 'pointer' }}>
            Pushback — use these answers, do not improvise
          </summary>
          <div className="mt-2">
            <Pushback q="So how much am I actually getting?">
              I'm not able to give you the figure on the call — the outcome gets confirmed to you at
              the end of this session. What I can tell you is the review's been done properly.
            </Pushback>
            <Pushback q="Can you change it? / Can you approve more?">
              The amount isn't something I set on the call. What I've done is make sure the dispute
              is recorded with the detail you've given me.
            </Pushback>
            <Pushback q="Why did the assistant not just deal with this?">
              It takes the details and passes anything disputed to one of us. That's why you're
              through to me.
            </Pushback>
            <Pushback q="Is this a real company? / Are you a real person?">
              I'm a real person, yes. This is a research session, so Salvena is a fictional insurer
              — but let's carry on with the claim as described.
            </Pushback>
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
