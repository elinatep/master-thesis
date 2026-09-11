// Browser-side voice config, served from configuration/voice/client.json (via
// GET /api/voice/client-config) so ALL voice config lives in one traceable place instead of
// being hardcoded here. Fetched once and cached; falls back to safe defaults if the backend
// can't be reached, so a config hiccup never kills the voice session.

export interface ClientVoiceConfig {
  /** OpenAI Realtime SDP exchange URL (browser -> OpenAI). */
  realtimeUrl: string
  /** getUserMedia audio constraints (echo cancellation etc. — affect VAD, hence latency). */
  audio: MediaTrackConstraints
  /**
   * Instruction for the bot's opening turn: on connect the client triggers one response so the
   * assistant introduces itself (the participant never speaks first). Kept identical across all
   * levels for cross-condition parity. Empty string = no automatic greeting.
   */
  greeting: string
}

const DEFAULTS: ClientVoiceConfig = {
  realtimeUrl: 'https://api.openai.com/v1/realtime/calls',
  audio: { echoCancellation: true, noiseSuppression: true, autoGainControl: true },
  greeting: '',
}

let cached: ClientVoiceConfig | null = null

export async function getClientVoiceConfig(): Promise<ClientVoiceConfig> {
  if (cached) return cached
  try {
    const res = await fetch('/api/voice/client-config')
    if (!res.ok) throw new Error(`client-config ${res.status}`)
    const data = (await res.json()) as Partial<ClientVoiceConfig>
    cached = {
      realtimeUrl: data.realtimeUrl ?? DEFAULTS.realtimeUrl,
      audio: data.audio ?? DEFAULTS.audio,
      greeting: data.greeting ?? DEFAULTS.greeting,
    }
  } catch (e) {
    console.debug('[voice] client-config fetch failed, using defaults', e)
    cached = DEFAULTS
  }
  return cached
}
