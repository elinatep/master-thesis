// Behavioural log client. Holds the session id and streams events + transcript lines to the
// backend write-as-you-go (the "logging completeness" constraint): a small debounced flush
// keeps it near-real-time, and a sendBeacon on page hide rescues the last batch when the tab
// closes or we redirect back to Qualtrics. A single monotonic `seq` orders everything within
// the session, even if requests arrive out of order server-side.

import type { Condition } from './session'

const FLUSH_DELAY_MS = 1000

interface QueuedEvent {
  seq: number
  type: string
  data: Record<string, unknown> | null
  clientTs: string
}

interface QueuedTranscript {
  seq: number
  role: 'USER' | 'ASSISTANT'
  text: string
  clientTs: string
}

let sessionId: number | null = null
let seq = 0
let flushTimer: number | null = null
let flushing = false
let unloadHooked = false
let errorsHooked = false
const eventQueue: QueuedEvent[] = []
const transcriptQueue: QueuedTranscript[] = []

/** Open the session at the backend and start logging. Throws if the session can't be created. */
export async function createSession(cond: Condition): Promise<void> {
  const res = await fetch('/api/study/session', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      participantId: cond.participantId,
      arm: cond.arm,
      userAgent: navigator.userAgent,
    }),
  })
  if (!res.ok) throw new Error(`Could not open study session (${res.status})`)
  const data = (await res.json()) as { sessionId: number }
  sessionId = data.sessionId
  hookUnload()
  hookGlobalErrors()
  console.debug('[study] session', sessionId, 'opened for', cond.participantId, 'arm', cond.arm)
}

/** Capture uncaught errors and promise rejections as ERROR events. */
function hookGlobalErrors(): void {
  if (errorsHooked) return
  errorsHooked = true
  window.addEventListener('error', (e) => {
    logEvent('ERROR', { where: 'window', message: e.message, source: e.filename, line: e.lineno })
  })
  window.addEventListener('unhandledrejection', (e) => {
    logEvent('ERROR', { where: 'unhandledrejection', message: String(e.reason) })
  })
}

export function getSessionId(): number | null {
  return sessionId
}

/**
 * Close the session out at handback: flush whatever is queued, then mark it complete
 * (end time + optional task outcome). Best-effort — never throws, so the caller can always
 * proceed to redirect the participant back to Qualtrics.
 */
export async function completeSession(taskSuccess?: boolean | null): Promise<void> {
  if (sessionId == null) return
  await flush()
  try {
    await postJson(`/api/study/session/${sessionId}/complete`, { taskSuccess: taskSuccess ?? null })
  } catch (e) {
    console.debug('[study] complete failed', e)
  }
}

/** Record one behavioural event (NAVIGATION, CLAIM_SUBMITTED, ERROR, VOICE_*, …). */
export function logEvent(type: string, data?: Record<string, unknown>): void {
  if (sessionId == null) return
  eventQueue.push({ seq: seq++, type, data: data ?? null, clientTs: new Date().toISOString() })
  scheduleFlush()
}

/** Record one voice transcript line (personal data — see the behavioural.transcript_entry table). */
export function logTranscript(role: 'USER' | 'ASSISTANT', text: string): void {
  if (sessionId == null) return
  const trimmed = text.trim()
  if (!trimmed) return
  transcriptQueue.push({ seq: seq++, role, text: trimmed, clientTs: new Date().toISOString() })
  scheduleFlush()
}

function scheduleFlush(): void {
  if (flushTimer != null) return
  flushTimer = window.setTimeout(() => {
    flushTimer = null
    void flush()
  }, FLUSH_DELAY_MS)
}

async function flush(): Promise<void> {
  if (flushing || sessionId == null) return
  if (eventQueue.length === 0 && transcriptQueue.length === 0) return
  flushing = true
  const ev = eventQueue.splice(0, eventQueue.length)
  const tr = transcriptQueue.splice(0, transcriptQueue.length)
  try {
    if (ev.length) await postJson('/api/study/events', { sessionId, events: ev })
    if (tr.length) await postJson('/api/study/transcript', { sessionId, entries: tr })
  } catch (e) {
    // Re-queue (seq keeps order) and try again on the next flush — don't drop data points.
    eventQueue.unshift(...ev)
    transcriptQueue.unshift(...tr)
    console.debug('[study] flush failed, will retry', e)
    scheduleFlush()
  } finally {
    flushing = false
  }
}

async function postJson(url: string, body: unknown): Promise<void> {
  const res = await fetch(url, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  })
  if (!res.ok) throw new Error(`${url} -> ${res.status}`)
}

/** On page hide, ship whatever is queued via sendBeacon (survives unload/redirect). */
function hookUnload(): void {
  if (unloadHooked) return
  unloadHooked = true
  const beacon = () => {
    if (sessionId == null) return
    if (eventQueue.length) {
      const events = eventQueue.splice(0, eventQueue.length)
      navigator.sendBeacon('/api/study/events', blob({ sessionId, events }))
    }
    if (transcriptQueue.length) {
      const entries = transcriptQueue.splice(0, transcriptQueue.length)
      navigator.sendBeacon('/api/study/transcript', blob({ sessionId, entries }))
    }
  }
  window.addEventListener('pagehide', beacon)
  document.addEventListener('visibilitychange', () => {
    if (document.visibilityState === 'hidden') beacon()
  })
}

function blob(body: unknown): Blob {
  return new Blob([JSON.stringify(body)], { type: 'application/json' })
}
