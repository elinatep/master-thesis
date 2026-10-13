import { useEffect, useState } from 'react'
import Banner from './Banner'
import OutcomePanel from './OutcomePanel'
import SalvenaShell from './SalvenaShell'
import { getOutcome, type Outcome } from './api'
import { logEvent } from '../study/log'
import { goTo } from '../study/navigation'
import { setStage } from '../study/stage'
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
 * <p>Two ways out, and the choice is real. Asking customer service to review it takes them to the
 * assistant and then to a person. Accepting it ends the task and returns them to Qualtrics - which
 * means they never meet either agent and contribute nothing to the 2x2, so the choice is recorded
 * as an event and carried back to Qualtrics in the URL.
 */
export default function OutcomeScreen() {
  const [outcome, setOutcome] = useState<Outcome | null>(null)
  const [error, setError] = useState<string | null>(null)
  // No confirm: accepting the assessment is itself the deliberate act, and asking them to confirm
  // a decision they just made reads as the page doubting them.
  const { finishing, finish } = useHandback({ confirm: false })

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
    setStage('call')
    goTo('/')
  }

  function acceptAssessment() {
    logEvent('ESCALATION_CHOICE', { contacted: false })
    void finish({ escalated: false })
  }

  return (
    <SalvenaShell>
      <Banner step={scripted} />
      <OutcomePanel step={scripted} />

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
    </SalvenaShell>
  )
}
