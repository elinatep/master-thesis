import { useEffect, useState } from 'react'
import Banner from './Banner'
import OutcomePanel from './OutcomePanel'
import SalvenaShell from './SalvenaShell'
import { getOutcome, type Outcome } from './api'
import { logEvent } from '../study/log'
import { getStage, setStage, subscribeStage } from '../study/stage'
import AssistantStage from '../call/AssistantStage'
import { useHandback } from '../study/useHandback'

/**
 * What Salvena tells the participant about the claim they just filed, and the decision that
 * follows it.
 *
 * <p>This screen is the setup, not the manipulation: every participant reads the same assessment,
 * in all four cells. The pre-test's job was to find an outcome that reliably produces anger
 * without reading as a bug, and this is the one that did. What varies between participants happens
 * afterwards, on the call.
 *
 * <p>Two ways out, and the choice is real. Asking customer service to review it opens the assistant
 * here, on this screen. Accepting it ends the task and returns them to Qualtrics - which means they
 * never meet either agent and contribute nothing to the 2x2, so the choice is recorded as an event
 * and carried back to Qualtrics in the URL.
 *
 * <p>The assessment stays on screen for the whole call, which is the point: it is what they are
 * disputing. Sending them back to the portal dashboard to talk about it would take away the
 * figures they are arguing from, and the agent's script has them refer to those figures.
 */
export default function OutcomeScreen() {
  const [outcome, setOutcome] = useState<Outcome | null>(null)
  const [error, setError] = useState<string | null>(null)
  // Whether the call has been asked for. Read from the shared stage rather than kept locally, so a
  // participant who reloads mid-call comes back to the call and not to the choice they already made.
  const [calling, setCalling] = useState(() => getStage() === 'call')
  // No confirm: accepting the assessment is itself the deliberate act, and asking them to confirm
  // a decision they just made reads as the page doubting them.
  const { finishing, finish } = useHandback({ confirm: false })

  useEffect(() => subscribeStage(() => setCalling(getStage() === 'call')), [])

  useEffect(() => {
    getOutcome()
      .then((o) => {
        setOutcome(o)
        logEvent('OUTCOME_SHOWN', {})
      })
      .catch((e: unknown) => setError(e instanceof Error ? e.message : String(e)))
  }, [])

  if (error) {
    return (
      <SalvenaShell>
        <div className="alert alert-warning" role="alert">
          {error}
        </div>
      </SalvenaShell>
    )
  }

  if (!outcome) {
    return (
      <SalvenaShell>
        <p className="text-muted">Loading…</p>
      </SalvenaShell>
    )
  }

  const { scripted } = outcome
  const labels = scripted.escalation

  function contactCustomerService() {
    // Logged before anything else can fail. Whether each participant asked for a review is the
    // difference between a usable row and an excluded one, so it must not depend on the call
    // starting successfully.
    logEvent('ESCALATION_CHOICE', { contacted: true })
    // No navigation: the assistant opens over this screen, and the assessment stays put.
    setStage('call')
  }

  function acceptAssessment() {
    logEvent('ESCALATION_CHOICE', { contacted: false })
    void finish({ escalated: false })
  }

  return (
    <SalvenaShell>
      <Banner step={scripted} />
      <OutcomePanel step={scripted} />

      {/*
        Once they have asked for the review the choice is gone, not just disabled: leaving "Accept
        the assessment and finish" on screen next to a live call invites ending the session
        mid-conversation, with a confederate on the line.
      */}
      {calling ? (
        <div className="card mt-3">
          <div className="card-body">
            <p className="mb-0">
              You asked for the assessment to be reviewed. Customer service is on the line - the
              panel is at the bottom right of the screen.
            </p>
          </div>
        </div>
      ) : (
      <div className="card mt-3">
        <div className="card-body">
          {labels?.note && <p className="mb-3">{labels.note}</p>}
          <div className="d-flex flex-wrap gap-2">
            <button className="btn btn-primary btn-lg" onClick={contactCustomerService}>
              {labels?.contact ?? 'Contact customer service'}
            </button>
            {/*
              Secondary, not hidden. A participant who wants to accept the assessment has to be
              able to - the alternative is a study that only looks like it offers a choice - but
              the review is the path the briefing points at, so it leads.
            */}
            <button
              className="btn btn-outline-secondary btn-lg"
              disabled={finishing}
              onClick={acceptAssessment}
            >
              {finishing ? 'One moment…' : (labels?.accept ?? 'Accept the assessment and finish')}
            </button>
          </div>
        </div>
      </div>
      )}

      {/*
        The assistant, and then the human employee. Renders nothing until the review is asked for
        (AssistantStage reads the shared stage), and is positioned against the viewport rather than
        this column, so it sits at the bottom right with the assessment still readable above it.
      */}
      <AssistantStage />
    </SalvenaShell>
  )
}
