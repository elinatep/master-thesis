import { useState } from 'react'
import type { ClaimFields } from './api'

/**
 * The claim form. Four fields, and a reminder box repeating the values the briefing gave.
 *
 * <p>The fields are NOT pre-filled, and what is typed is not validated against the briefing.
 * Copying the details across is the effort the participant invests in the claim - which is what
 * makes the unfavourable outcome land, and, in the failure arm, what makes being told to do it
 * again irritating. Pre-filling would remove the very thing being manipulated.
 */
export default function ClaimForm({
  heading,
  notice,
  instruction,
  submitting,
  onSubmit,
}: {
  heading: string
  notice?: string
  instruction?: string
  submitting: boolean
  onSubmit: (fields: ClaimFields) => void
}) {
  const [fields, setFields] = useState<ClaimFields>({
    typeOfDamage: '',
    cause: '',
    incidentDate: '',
    estimatedDamage: '',
  })

  function set(key: keyof ClaimFields, value: string) {
    setFields((f) => ({ ...f, [key]: value }))
  }

  const complete = Object.values(fields).every((v) => v.trim() !== '')

  return (
    <div className="card">
      <div className="card-body">
        <h1 className="h5">{heading}</h1>
        {notice && <p className="text-muted mb-2">{notice}</p>}
        {instruction && <p className="mb-3">{instruction}</p>}

        <div className="border rounded p-3 mb-4 bg-light">
          <div className="fw-semibold mb-2">Please enter the details of your claim exactly as given below.</div>
          <dl className="row mb-0 small">
            <dt className="col-5 col-sm-4 fw-normal text-muted">Type of damage</dt>
            <dd className="col-7 col-sm-8 mb-1">Water damage</dd>
            <dt className="col-5 col-sm-4 fw-normal text-muted">Cause</dt>
            <dd className="col-7 col-sm-8 mb-1">Burst water pipe</dd>
            <dt className="col-5 col-sm-4 fw-normal text-muted">Date of incident</dt>
            <dd className="col-7 col-sm-8 mb-1">Yesterday</dd>
            <dt className="col-5 col-sm-4 fw-normal text-muted">Estimated damage</dt>
            <dd className="col-7 col-sm-8 mb-0">1,200 scenario pounds</dd>
          </dl>
        </div>

        <form
          onSubmit={(e) => {
            e.preventDefault()
            if (complete && !submitting) onSubmit(fields)
          }}
        >
          <Field label="Type of damage" value={fields.typeOfDamage}
                 onChange={(v) => set('typeOfDamage', v)} disabled={submitting} />
          <Field label="Cause" value={fields.cause}
                 onChange={(v) => set('cause', v)} disabled={submitting} />
          <Field label="Date of incident" value={fields.incidentDate}
                 onChange={(v) => set('incidentDate', v)} disabled={submitting} />
          <Field label="Estimated damage (scenario pounds)" value={fields.estimatedDamage}
                 onChange={(v) => set('estimatedDamage', v)} disabled={submitting} />

          <p className="text-muted small">
            Please use only the information provided in the study and do not enter real personal
            information.
          </p>

          <button type="submit" className="btn btn-primary" disabled={!complete || submitting}>
            {submitting ? 'Submitting…' : 'Submit claim'}
          </button>
        </form>
      </div>
    </div>
  )
}

function Field({
  label,
  value,
  onChange,
  disabled,
}: {
  label: string
  value: string
  onChange: (v: string) => void
  disabled: boolean
}) {
  const id = label.replace(/\W+/g, '-').toLowerCase()
  return (
    <div className="mb-3">
      <label className="form-label" htmlFor={id}>
        {label}
      </label>
      <input
        id={id}
        className="form-control"
        value={value}
        disabled={disabled}
        autoComplete="off"
        onChange={(e) => onChange(e.target.value)}
      />
    </div>
  )
}
