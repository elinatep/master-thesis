// Voice-bot integration level (CLAUDE.md core IV). It arrives via the Qualtrics handover token and
// is recovered once, at the intake gate, into the session Condition (see study/session.ts). We read
// it from there rather than the URL: the level must be fixed for the whole session, and it never
// appears in the URL under the token handover. All portal code that calls getLevel() runs only
// after the gate has resolved the condition, so the value is available synchronously by then.
// (On the ungated pages — /data, /news — no condition is resolved, so getLevel() reports 0.)

import { getCondition } from './session'

export type Level = 0 | 1 | 2 | 3

/** The resolved level, or null if no valid condition has been recovered (the gate treats null as fatal). */
export function getLevelParam(): Level | null {
  return getCondition()?.level ?? null
}

/** The assigned level for app rendering; 0 when no condition is resolved. */
export function getLevel(): Level {
  return getLevelParam() ?? 0
}

// Dev convenience: read the captured level from the DevTools console. Dev builds only.
if (import.meta.env.DEV) {
  Object.assign(window, { getLevel, getLevelParam })
}
