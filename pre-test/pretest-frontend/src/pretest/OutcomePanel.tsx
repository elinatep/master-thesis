import type { ScriptedStep } from './api'

/**
 * The assessment panel. Every word, every row and every figure comes from the arm file - this
 * renders, it does not decide. That is what lets the wording be corrected between pre-test rounds
 * without a rebuild, which is most of what a pre-test produces.
 */
export default function OutcomePanel({ step }: { step: ScriptedStep }) {
  if (!step.panel) return null
  const { heading, rows, body } = step.panel
  return (
    <div className="card mt-3">
      <div className="card-body">
        <h2 className="h5 mb-3">{heading}</h2>

        {rows.length > 0 && (
          <dl className="row mb-3">
            {rows.map((r) => (
              <Row key={r.label} label={r.label} value={r.value} emphasis={r.emphasis} />
            ))}
          </dl>
        )}

        {body.map((paragraph) => (
          <p key={paragraph} className="mb-2">
            {paragraph}
          </p>
        ))}
      </div>
    </div>
  )
}

function Row({ label, value, emphasis }: { label: string; value: string; emphasis?: boolean }) {
  return (
    <>
      <dt className="col-6 col-sm-5 fw-normal text-muted">{label}</dt>
      <dd className={`col-6 col-sm-7 mb-1 ${emphasis ? 'fw-bold' : ''}`}>{value}</dd>
    </>
  )
}
