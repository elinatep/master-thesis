import type { ReactNode } from 'react'
import TaskCard from './TaskCard'
import { TASK_RAIL_WIDTH } from './layout'

// Splits the screen: the portal on the left, the task card on the right.
//
// Level-independent on purpose. At level 3 the portal renders no GUI at all and the study's own
// voice screen fills the left pane instead — but the participant still needs the task in front of
// them, and the card being present in every condition means its presence cannot explain any
// difference between them.
//
// The wrapper lives here rather than in the portal. The portal is a library that renders into
// whatever box it is given: `AppLayout` is `min-vh-100` inside a `Container`, so it reflows into a
// narrower column with no change on that side, and nothing about a task rail belongs in a
// component that must know nothing about experiments.
//
// Each pane scrolls on its own (`height: 100vh` + `overflow: auto`), for two reasons: the card
// must never scroll out of view, which is the entire point of it; and the portal's navbar is
// `sticky top`, which pins to its own scroll container — so the navbar stays put within the left
// pane exactly as it does full-screen.

export default function StudySplit({ children }: { children: ReactNode }) {
  return (
    <div
      style={{
        display: 'grid',
        // minmax(0, 1fr) rather than 1fr: grid items default to min-content width, and a wide table
        // inside the portal would otherwise push the column open and squeeze the rail.
        gridTemplateColumns: `minmax(0, 1fr) ${TASK_RAIL_WIDTH}px`,
        height: '100vh',
      }}
    >
      <main style={{ overflowY: 'auto' }}>{children}</main>
      <TaskCard />
    </div>
  )
}
