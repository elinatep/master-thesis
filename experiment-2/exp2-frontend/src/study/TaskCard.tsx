import { useEffect, useState } from 'react'
import { fetchTask, type Task } from './task'
import { logEvent } from './log'

// The task card: what the participant was asked to do, kept on screen for the whole session.
//
// It exists because the instruction is given in Qualtrics and then left behind — participants
// arrive at the portal and, a few minutes in, can no longer remember the exact wording. It is
// deliberately inert: no buttons, no links, nothing to click. Anything interactive here would be
// apparatus the participant could act on, and it would behave differently at level 3 (where there
// is no screen to click), so an identical, static card is the only version that is the same in
// every condition.

export default function TaskCard() {
  const [task, setTask] = useState<Task | null>(null)
  const [failed, setFailed] = useState(false)

  useEffect(() => {
    let live = true
    fetchTask()
      .then((t) => live && setTask(t))
      .catch((e: unknown) => {
        if (!live) return
        setFailed(true)
        // Worth a row in the behavioural log: a session run without the task visible is not
        // comparable to one run with it, and this is the only trace of that afterwards.
        logEvent('TASK_LOAD_FAILED', { error: e instanceof Error ? e.message : String(e) })
      })
    return () => {
      live = false
    }
  }, [])

  return (
    <aside
      aria-label="Your task"
      className="h-100 border-start bg-body-tertiary"
      style={{ overflowY: 'auto' }}
    >
      <div className="p-4">
        <h2 className="h5 mb-3">{task?.title ?? 'Your task'}</h2>

        {task &&
          task.paragraphs.map((p, i) => (
            <p key={i} className="mb-3" style={{ lineHeight: 1.6 }}>
              {p}
            </p>
          ))}

        {!task && !failed && <p className="text-muted mb-0">Loading…</p>}

        {failed && (
          <p className="text-muted mb-0">
            The task text could not be loaded. Please refer to the instructions in the survey.
          </p>
        )}
      </div>
    </aside>
  )
}
