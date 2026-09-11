// The participant condition, recovered from the Qualtrics handover. Qualtrics registers the
// condition server-side (POST /api/handover) and redirects the participant here with an opaque
// ?t=; on entry we exchange that token (GET /api/handover/{token}) for the condition and cache
// it for the whole session. Nothing sensitive travels in the URL, and the browser never parses the
// participant id or the assigned cell itself.
//
// Strict intake (study decision): a missing or unknown/expired token is fatal — the gate renders an
// error and does NOT open a session, rather than logging orphaned data under no/wrong participant.

export interface Condition {
  participantId: string
  /** Does the AI voice assistant acknowledge the participant's negative affect? */
  aiAck: boolean
  /** Does the human employee acknowledge it after the handover? */
  humanAck: boolean
  /** Policyholder display name from the handover; null falls back to the shared persona. */
  name: string | null
  /** Where to send the participant back to on completion (Qualtrics). Used in the handback. */
  callbackUrl: string | null
}

let cached: Condition | null = null
let token: string | null = null
let invalidReason = ''
let inFlight: Promise<Condition | null> | null = null

// Strictly booleans, and nothing else. Not `Boolean(v)`, not `v === 'true'` with a fallback: a
// factor that arrived malformed has to fail intake, not quietly resolve to false. Defaulting would
// drop the participant into a real cell of the 2x2 that nobody assigned them to, and the
// behavioural log would record that cell as though it had been assigned.
function parseFactor(v: unknown): boolean | null {
  return typeof v === 'boolean' ? v : null
}

/**
 * Exchange the ?t= for the handover condition. Runs at most once (the promise is memoised, so
 * repeat/concurrent callers share one exchange) and caches the result (or null on any failure, with
 * a reason). Reusable token: a reload re-exchanges cleanly.
 */
export function resolveCondition(): Promise<Condition | null> {
  if (!inFlight) inFlight = exchange()
  return inFlight
}

async function exchange(): Promise<Condition | null> {
  token = new URLSearchParams(window.location.search).get('t')
  if (!token) {
    invalidReason = 'no handover token in the URL'
    return null
  }
  try {
    const res = await fetch(`/api/handover/${encodeURIComponent(token)}`)
    if (res.status === 404) {
      invalidReason = 'the handover token is unknown or has expired'
      return null
    }
    if (!res.ok) {
      invalidReason = `the handover exchange failed (${res.status})`
      return null
    }
    const d = (await res.json()) as {
      participantId?: string
      aiAck?: unknown
      humanAck?: unknown
      name?: string | null
      callbackUrl?: string | null
    }
    const participantId = (d.participantId ?? '').trim()
    const aiAck = parseFactor(d.aiAck)
    const humanAck = parseFactor(d.humanAck)
    if (!participantId || aiAck === null || humanAck === null) {
      invalidReason = 'the handover data is incomplete (participant id or assigned condition)'
      return null
    }
    cached = {
      participantId,
      aiAck,
      humanAck,
      name: (d.name ?? '').trim() || null,
      callbackUrl: d.callbackUrl ?? null,
    }
    return cached
  } catch {
    invalidReason = 'could not reach the handover endpoint'
    return null
  }
}

/** The resolved condition, or null before resolution / on failure (see resolveCondition). */
export function getCondition(): Condition | null {
  return cached
}

/**
 * Short label for the assigned cell — "AI+/H-" and so on — for logs and the researcher's export.
 * Display only; the two booleans are the data.
 */
export function cellLabel(c: Pick<Condition, 'aiAck' | 'humanAck'>): string {
  return `${c.aiAck ? 'AI+' : 'AI-'}/${c.humanAck ? 'H+' : 'H-'}`
}

/** The raw handover token from the entry URL, so a fresh re-entry (admin reset) can reuse it. */
export function getHandoverToken(): string | null {
  return token
}

/** Why intake is invalid — for a precise fail-loud message at the gate. */
export function describeInvalidCondition(): string {
  return invalidReason || 'missing handover token'
}

// Dev convenience: read the captured condition from the DevTools console. Dev builds only.
if (import.meta.env.DEV) {
  Object.assign(window, { getCondition, cellLabel })
}
