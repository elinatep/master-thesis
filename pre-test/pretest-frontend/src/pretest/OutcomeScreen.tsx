import { useEffect, useState } from 'react'
import Banner from './Banner'
import OutcomePanel from './OutcomePanel'
import SalvenaShell from './SalvenaShell'
import { getOutcome, type Outcome } from './api'
import { logEvent } from '../study/log'
import { goTo } from '../study/navigation'
import { useHandback } from '../study/useHandback'

/**
 * What Salvena tells the participant about the claim they just filed. This screen is the
 * manipulation, and every word on it comes from the arm file.
 *
 * <p>Two endings. When the arm is finished with them, a Continue button closes the session and
 * returns them to Qualtrics for the emotion measures. When it is not - only S4, after the first
 * failure - they are sent back into the portal's claim form to try again, which remounts empty, as
 * the error text says it would.
 */
export default function OutcomeScreen() {
  const [outcome, setOutcome] = useState<Outcome | null>(null)
  const [error, setError] = useState<string | null>(null)
  const { finishing, finish } = useHandback()

  useEffect(() => {
    getOutcome()
      .then((o) => {
        setOutcome(o)
        logEvent('OUTCOME_SHOWN', {
          arm: o.arm,
          attempt: o.attemptNumber,
          result: o.scripted.result,
          finalAttempt: o.finalAttempt,
        })
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

  const { scripted, finalAttempt } = outcome

  return (
    <SalvenaShell>
      <Banner step={scripted} />
      {scripted.retry && !finalAttempt ? (
        <div className="card">
          <div className="card-body">
            <h1 className="h5">{scripted.retry.heading}</h1>
            <p className="text-muted mb-2">{scripted.retry.notice}</p>
            <p className="mb-4">{scripted.retry.instruction}</p>
            <button
              className="btn btn-primary"
              onClick={() => {
                logEvent('CLAIM_RETRY_STARTED', { arm: outcome.arm, attempt: outcome.attemptNumber })
                goTo('/claims/new')
              }}
            >
              Try again
            </button>
          </div>
        </div>
      ) : (
        <>
          <OutcomePanel step={scripted} />
          <div className="mt-4">
            <button className="btn btn-primary" onClick={() => void finish()} disabled={finishing}>
              {finishing ? 'Returning…' : 'Continue'}
            </button>
          </div>
        </>
      )}
    </SalvenaShell>
  )
}
