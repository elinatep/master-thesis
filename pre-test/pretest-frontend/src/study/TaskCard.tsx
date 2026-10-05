import { useEffect, useState } from 'react'
import { loadTask, type TaskBlock, type TaskSpan } from './task'
import { isAdminVisible } from './adminVisibility'

/**
 * The scenario, kept on screen for the whole run.
 *
 * <p>Qualtrics shows the briefing once and then hands the participant to a portal they have never
 * seen, where they have to find a policy, choose a claim type and enter an amount. By the time they
 * reach the form the briefing is several screens behind them and unreachable - the survey has
 * ended. A participant who misremembers the amount files a different claim from the one the study
 * assigned, and the arm's outcome is then about a claim nobody designed.
 *
 * <p>Rendered through the portal's {@code assistant} seam, which is host chrome drawn over the
 * portal shell on every page. That is the whole reason it is here and not in a portal component:
 * the portal is the thing being studied and must stay the same portal in every arm, while this is
 * apparatus.
 *
 * <p>Collapsible, and it remembers the choice. Someone who wants the screen for the form should be
 * able to have it, and someone who wants to re-read the amount should not have to remember where it
 * went. It starts open, because the failure it prevents is worse than the inconvenience it causes.
 */
export default function TaskCard() {
  const [blocks, setBlocks] = useState<TaskBlock[] | null>(null)
  const [failed, setFailed] = useState(false)
  const [open, setOpen] = useState(() => readOpen())

  useEffect(() => {
    let live = true
    loadTask()
      .then((b) => live && setBlocks(b))
      .catch(() => live && setFailed(true))
    return () => {
      live = false
    }
  }, [])

  // The researcher's own pages are not a participant run; a scenario card over the data export is
  // just in the way.
  if (isAdminVisible() && window.location.pathname.startsWith('/data')) return null

  // A card that failed to load says so rather than rendering an empty box: a participant working
  // without the task in front of them is exactly what this exists to prevent, and silence would
  // make it look like a design choice.
  if (failed) {
    return (
      <Shell open onToggle={() => {}}>
        <p className="mb-0 small text-danger">
          The task could not be loaded. Please continue with the scenario as described in the survey.
        </p>
      </Shell>
    )
  }

  if (!blocks) return null

  return (
    <Shell
      open={open}
      onToggle={() => {
        const next = !open
        setOpen(next)
        writeOpen(next)
      }}
    >
      {blocks.map((block, i) => {
        if (block.kind === 'heading') return null // the shell shows the title
        if (block.kind === 'paragraph') {
          return (
            <p key={i} className="small mb-2">
              <Spans spans={block.spans} />
            </p>
          )
        }
        return (
          <ul key={i} className="small mb-2 ps-3">
            {block.items.map((item, j) => (
              <li key={j}>
                <Spans spans={item} />
              </li>
            ))}
          </ul>
        )
      })}
    </Shell>
  )
}

function Spans({ spans }: { spans: TaskSpan[] }) {
  return (
    <>
      {spans.map((s, i) => (s.bold ? <strong key={i}>{s.text}</strong> : <span key={i}>{s.text}</span>))}
    </>
  )
}

function Shell({
  open,
  onToggle,
  children,
}: {
  open: boolean
  onToggle: () => void
  children: React.ReactNode
}) {
  return (
    <aside
      className="card shadow-sm border-primary-subtle"
      style={{
        position: 'fixed',
        // Below the navbar and clear of the right edge. Fixed rather than sticky so it survives the
        // participant scrolling a long claims table.
        top: '5rem',
        right: '1rem',
        width: 'min(20rem, calc(100vw - 2rem))',
        zIndex: 1030,
      }}
      aria-label="Your task"
    >
      <div className="card-header d-flex align-items-center justify-content-between py-2">
        <span className="fw-semibold small">Your task</span>
        <button
          type="button"
          className="btn btn-sm btn-link p-0 text-decoration-none small"
          onClick={onToggle}
          aria-expanded={open}
        >
          {open ? 'Hide' : 'Show'}
        </button>
      </div>
      {open && <div className="card-body py-2">{children}</div>}
    </aside>
  )
}

// Per-browser, not per-participant: whether the card is folded away is a convenience, and nothing
// about it belongs in the behavioural data. Wrapped because storage throws in a private window.
const KEY = 'pretest.task.open'

function readOpen(): boolean {
  try {
    return localStorage.getItem(KEY) !== '0'
  } catch {
    return true
  }
}

function writeOpen(open: boolean): void {
  try {
    localStorage.setItem(KEY, open ? '1' : '0')
  } catch {
    /* ignore - the card still works, it just forgets */
  }
}
