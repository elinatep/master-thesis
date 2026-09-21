import type { ScriptedStep } from './api'

/** The success/error strip above the panel. Wording comes from the arm file, never from here. */
export default function Banner({ step }: { step: ScriptedStep }) {
  if (!step.banner) return null
  const error = step.banner.kind === 'error'
  return (
    <div className={`alert ${error ? 'alert-danger' : 'alert-success'}`} role="alert">
      <div className="fw-semibold">
        {error ? '⚠ ' : '✓ '}
        {step.banner.text}
      </div>
      {step.bannerDetail && <div className="mt-1">{step.bannerDetail}</div>}
    </div>
  )
}
