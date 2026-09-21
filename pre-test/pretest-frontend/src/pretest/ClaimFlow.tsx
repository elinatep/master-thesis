import { useEffect, useState } from 'react'
import Banner from './Banner'
import ClaimForm from './ClaimForm'
import OutcomePanel from './OutcomePanel'
import SalvenaShell from './SalvenaShell'
import { getFormState, submitClaim, type ClaimFields, type Outcome, type ScriptedStep } from './api'
import { logEvent } from '../study/log'
import { useHandback } from '../study/useHandback'

/**
 * The whole platform half of the pre-test: file a claim, see what this arm does with it, and in
 * the failure arm, do it again. Then back to Qualtrics for the emotion measures.
 *
 * <p>The flow is linear and the server decides every step of it, so this component holds almost no
 * logic - which is the point. Anything decided here rather than server-side would be decided again
 * differently on a reload.
 */
export default function ClaimFlow() {
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)

  /** The step being shown above the form (a previous failure), if any. */
  const [priorStep, setPriorStep] = useState<ScriptedStep | null>(null)
  /** Set once the arm has finished with this participant. */
  const [finished, setFinished] = useState<Outcome | null>(null)
  const [retry, setRetry] = useState<ScriptedStep['retry'] | null>(null)

  const { finishing, finish } = useHandback()

  useEffect(() => {
    getFormState()
      .then((state) => {
        // Resuming mid-arm (a reload after a failure): show what they already saw.
        if (state.previousOutcome) {
          setPriorStep(state.previousOutcome)
          setRetry(state.previousOutcome.retry ?? null)
        }
        logEvent('CLAIM_FORM_SHOWN', { attempt: state.attemptNumber, totalAttempts: state.totalAttempts })
        setLoading(false)
      })
      .catch((e: unknown) => {
        setError(e instanceof Error ? e.message : String(e))
        setLoading(false)
      })
  }, [])

  async function onSubmit(fields: ClaimFields) {
    setSubmitting(true)
    setError(null)
    try {
      const outcome = await submitClaim(fields)
      if (outcome.finalAttempt) {
        setFinished(outcome)
        setPriorStep(null)
        setRetry(null)
      } else {
        // Another attempt to come. Show the failure, then the empty form again - the arm file's
        // own words say the entered information was not saved, so the form must genuinely be empty.
        setPriorStep(outcome.scripted)
        setRetry(outcome.scripted.retry ?? null)
      }
      window.scrollTo({ top: 0, behavior: 'smooth' })
    } catch (e: unknown) {
      setError(e instanceof Error ? e.message : String(e))
    } finally {
      setSubmitting(false)
    }
  }

  if (loading) {
    return (
      <SalvenaShell>
        <p className="text-muted">Loading…</p>
      </SalvenaShell>
    )
  }

  if (finished) {
    return (
      <SalvenaShell>
        <Banner step={finished.scripted} />
        <OutcomePanel step={finished.scripted} />
        <div className="mt-4">
          <button className="btn btn-primary" onClick={() => void finish()} disabled={finishing}>
            {finishing ? 'Returning…' : 'Continue'}
          </button>
        </div>
      </SalvenaShell>
    )
  }

  return (
    <SalvenaShell>
      {priorStep && <Banner step={priorStep} />}
      {error && (
        <div className="alert alert-warning" role="alert">
          {error}
        </div>
      )}
      <ClaimForm
        key={retry ? 'retry' : 'first'}
        heading={retry?.heading ?? 'File your claim'}
        notice={retry?.notice}
        instruction={retry?.instruction}
        submitting={submitting}
        onSubmit={(fields) => void onSubmit(fields)}
      />
    </SalvenaShell>
  )
}
