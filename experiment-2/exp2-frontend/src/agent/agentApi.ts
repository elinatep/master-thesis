// The agent console's calls. Everything here is behind the agent credential (HTTP Basic): the
// browser is challenged when /agent is opened, and the cached credentials ride along on these
// fetches for the rest of the session.
//
// No participant header on any of these - the console is not a participant, and these endpoints
// identify the handover by id.

export interface QueueEntry {
  handoverId: number
  participantId: string
  sessionId: number
  /** The agent's own instruction. There is deliberately no AI factor here - see Brief. */
  humanAck: boolean | null
  summary: Record<string, unknown> | null
  requestedAt: string
  waitedSeconds: number
}

export interface TranscriptLine {
  role: string
  text: string
  at: string | null
}

/**
 * One handover, as the console shows it.
 *
 * Note what is absent: whether the AI assistant acknowledged the participant. That is the study's
 * decision, not an oversight - the agent reads the transcript and will often infer it, and the
 * point is that what they are *told* is the same every session rather than printed on the card.
 * The server asserts the absence in `AgentConsoleTest`; do not add it here either.
 */
export interface Brief extends QueueEntry {
  transcript: TranscriptLine[]
  pickedUpAt: string | null
  agentId: string | null
  endedAt: string | null
  callId: string
}

async function json<T>(url: string, init?: RequestInit): Promise<T> {
  const res = await fetch(url, init)
  if (!res.ok) {
    // The status matters to the caller: 409 is "somebody else got there first", which is a normal
    // thing to happen and not an error to panic the console with.
    const err = new Error(`${res.status} ${res.statusText}`) as Error & { status?: number }
    err.status = res.status
    throw err
  }
  return (await res.json()) as T
}

export function fetchQueue(): Promise<QueueEntry[]> {
  return json<QueueEntry[]>('/api/agent/queue')
}

export function pickUp(handoverId: number): Promise<Brief> {
  return json<Brief>(`/api/agent/handover/${handoverId}/pickup`, { method: 'POST' })
}

export function fetchBrief(handoverId: number): Promise<Brief> {
  return json<Brief>(`/api/agent/handover/${handoverId}`)
}

export function endCall(handoverId: number): Promise<Brief> {
  return json<Brief>(`/api/agent/handover/${handoverId}/end`, { method: 'POST' })
}

/** Where this handover's call token comes from. Handed to useAgentCall, which does the POST. */
export function callTokenUrl(handoverId: number): string {
  return `/api/agent/handover/${handoverId}/call-token`
}
