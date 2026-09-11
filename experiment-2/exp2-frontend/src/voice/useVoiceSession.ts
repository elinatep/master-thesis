import { useEffect, useRef, useState } from 'react'
import { logEvent, logTranscript } from '../study/log'
import { participantHeaders } from '../study/headers'
import { getClientVoiceConfig } from './voiceConfig'
import { getCondition } from '../study/session'

export type VoiceStatus = 'idle' | 'connecting' | 'live' | 'error'

interface RealtimeEvent {
  type?: string
  transcript?: string
  response?: { output?: RealtimeOutputItem[] }
  [k: string]: unknown
}

interface RealtimeOutputItem {
  type?: string
  name?: string
  call_id?: string
  arguments?: string
}

/**
 * Map a Realtime event type to who spoke, for final transcript events only.
 * User:      conversation.item.input_audio_transcription.completed|done
 * Assistant: response.(output_)audio_transcript.done
 * Returns null for anything else (deltas, non-transcript events).
 */
function transcriptRole(type?: string): 'USER' | 'ASSISTANT' | null {
  const t = type ?? ''
  if (t.includes('input_audio_transcription') && (t.endsWith('completed') || t.endsWith('done'))) return 'USER'
  if (t.includes('audio_transcript') && t.endsWith('done')) return 'ASSISTANT'
  return null
}

/**
 * Browser-side Realtime voice session over WebRTC (direct to OpenAI).
 * Flow: get ephemeral token from our backend -> RTCPeerConnection (mic + remote audio)
 * -> data channel "oai-events" -> SDP offer to OpenAI -> set answer.
 */
export function useVoiceSession() {
  const [status, setStatus] = useState<VoiceStatus>('idle')
  const [error, setError] = useState<string | null>(null)

  const pcRef = useRef<RTCPeerConnection | null>(null)
  const dcRef = useRef<RTCDataChannel | null>(null)
  const audioRef = useRef<HTMLAudioElement | null>(null)
  const streamRef = useRef<MediaStream | null>(null)
  const autoStarted = useRef(false)

  // Auto-start: the session opens as soon as the voice surface mounts — the participant never
  // clicks "start". The ref guard ensures a single session even under React StrictMode's
  // double effect invocation in dev. (The study laptop has the mic pre-permitted in Chrome.)
  useEffect(() => {
    if (autoStarted.current) return
    autoStarted.current = true
    void start()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  // Behavioural log: record every voice-session status transition (connecting/live/idle/error).
  useEffect(() => {
    logEvent('VOICE_STATUS', { status })
  }, [status])

  function send(obj: unknown) {
    dcRef.current?.send(JSON.stringify(obj))
  }

  /**
   * Execute one assistant function call and hand the result back as function_call_output.
   *
   * There is exactly one tool in Experiment 2 — transfer_to_human_agent — and it is a study
   * action, not a portal action: the assistant never drives the GUI or touches the participant's
   * insurance data. (levels-of-ai-help's nineteen GUI/backend tools and its guiBridge dispatch are
   * deliberately gone; keeping them would give the assistant capabilities the design does not vary
   * and the prompts never mention.)
   *
   * The call's arguments are the AI's handover summary, and they are logged before anything else
   * can fail. That log line is the record of what the human colleague was told, which is what the
   * handover-continuity items are ultimately about — so it must survive even if the POST below
   * does not.
   */
  async function executeAndReply(call: RealtimeOutputItem) {
    if (!call.name || !call.call_id) return
    console.debug('[ui] tool call:', call.name, 'args=', call.arguments)
    logEvent('VOICE_TOOL_CALL', { name: call.name, args: call.arguments ?? null })

    let result: unknown
    try {
      result =
        call.name === 'transfer_to_human_agent'
          ? await requestHumanAgent(call)
          : { error: `Unknown tool: ${String(call.name)}` }
    } catch (e) {
      result = { error: e instanceof Error ? e.message : String(e) }
    }

    console.debug('[ui] tool result:', call.name, '->', result)
    const errored = typeof result === 'object' && result !== null && 'error' in result
    logEvent('VOICE_TOOL_RESULT', {
      name: call.name,
      ok: !errored,
      error: errored ? String((result as { error: unknown }).error) : null,
    })
    send({
      type: 'conversation.item.create',
      item: { type: 'function_call_output', call_id: call.call_id, output: JSON.stringify(result) },
    })
  }

  /**
   * Put the participant in the queue for a human agent, handing over what the AI learned.
   *
   * Note what is NOT conditional here: the summary is sent in full in all four cells. Whether the
   * human acknowledges how the participant feels is the manipulation, and it is delivered to the
   * agent in their console — not by starving them of context. Continuity and acknowledgement are
   * different things, and confusing them would make the two factors impossible to separate.
   */
  async function requestHumanAgent(call: RealtimeOutputItem): Promise<unknown> {
    const res = await fetch('/api/study/agent-handover', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', ...participantHeaders() },
      body: JSON.stringify({ summary: call.arguments ?? '{}' }),
    })
    if (!res.ok) return { error: `Could not reach the agent queue (${res.status})` }
    return await res.json()
  }

  function handleEvent(evt: RealtimeEvent) {
    // Verbose for now; Phase 4 will persist transcripts to the behavioural schema.
    console.debug('[voice]', evt.type, evt)

    // Function calls arrive on the completed response. Executed one at a time: the transfer is
    // not idempotent — each call is a queue entry and a handover summary — so overlapping
    // executions could enqueue a participant twice. Then ask the model to continue
    // (response.create) so it speaks the outcome.
    if (evt.type === 'response.done') {
      const calls = (evt.response?.output ?? []).filter((o) => o.type === 'function_call')
      if (calls.length) {
        void (async () => {
          for (const call of calls) await executeAndReply(call)
          send({ type: 'response.create' })
        })()
      }
    }

    // Transcript: the Realtime channel emits a final transcript for each turn — the user's
    // recognised speech (input_audio_transcription) and the assistant's spoken reply
    // (…audio_transcript). Relay finals to the behavioural log (write-as-you-go), tagged with
    // who spoke. The transcript is NOT shown to the participant — capture only. Partial deltas
    // (no `transcript` field) are skipped.
    if (typeof evt.transcript === 'string' && evt.transcript.trim()) {
      const role = transcriptRole(evt.type)
      if (role) {
        logTranscript(role, evt.transcript.trim())
      }
    }
  }

  async function start() {
    setError(null)
    setStatus('connecting')
    try {
      // 0. Browser-side voice config (mic constraints, Realtime URL) from configuration/.
      const voiceCfg = await getClientVoiceConfig()

      // 1. Ephemeral token from our backend (long-lived key stays server-side).
      // Pass the AI-acknowledgement factor so the backend composes that cell's instructions. The
      // human-acknowledgement factor is deliberately not sent: it belongs to the second leg, and
      // the assistant must not know how its colleague is going to behave.
      const cond = getCondition()
      if (!cond) throw new Error('No resolved condition — cannot open a voice session')
      const tokenRes = await fetch(`/api/voice/token?aiAck=${cond.aiAck}`, { method: 'POST' })
      if (!tokenRes.ok) throw new Error(`Token request failed (${tokenRes.status})`)
      const tokenData = (await tokenRes.json()) as { value?: string }
      const ephemeral = tokenData.value
      if (!ephemeral) throw new Error('No ephemeral token in response')

      // 2. Peer connection + remote audio playback.
      const pc = new RTCPeerConnection()
      pcRef.current = pc

      const audioEl = audioRef.current ?? new Audio()
      audioEl.autoplay = true
      audioRef.current = audioEl
      pc.ontrack = (e) => {
        audioEl.srcObject = e.streams[0]
      }

      pc.onconnectionstatechange = () => {
        console.debug('[voice] connectionState:', pc.connectionState)
        // 'disconnected' is often a transient ICE blip that recovers — don't tear down on it.
        if (pc.connectionState === 'connected') setStatus('live')
        else if (pc.connectionState === 'failed' || pc.connectionState === 'closed') setStatus('idle')
      }
      pc.oniceconnectionstatechange = () => console.debug('[voice] iceConnectionState:', pc.iceConnectionState)

      // 3. Microphone (capture constraints from config — echo cancellation etc. affect VAD).
      const stream = await navigator.mediaDevices.getUserMedia({ audio: voiceCfg.audio })
      streamRef.current = stream
      stream.getTracks().forEach((t) => pc.addTrack(t, stream))

      // 4. Data channel for Realtime events.
      const dc = pc.createDataChannel('oai-events')
      dcRef.current = dc
      dc.onopen = () => {
        console.debug('[voice] data channel open')
        // Make the bot speak first so the participant knows they can talk to it. We trigger a
        // single response whose instructions are the configured greeting; with server VAD the
        // model would otherwise stay silent until spoken to. Identical across cells (parity).
        if (voiceCfg.greeting) {
          send({ type: 'response.create', response: { instructions: voiceCfg.greeting } })
        }
      }
      dc.onclose = () => console.debug('[voice] data channel close')
      dc.onerror = (e) => console.debug('[voice] data channel error', e)
      dc.onmessage = (e) => handleEvent(JSON.parse(e.data) as RealtimeEvent)

      // 5. SDP offer -> OpenAI -> answer.
      const offer = await pc.createOffer()
      await pc.setLocalDescription(offer)

      const sdpRes = await fetch(voiceCfg.realtimeUrl, {
        method: 'POST',
        body: offer.sdp,
        headers: {
          Authorization: `Bearer ${ephemeral}`,
          'Content-Type': 'application/sdp',
        },
      })
      if (!sdpRes.ok) throw new Error(`SDP exchange failed (${sdpRes.status})`)
      await pc.setRemoteDescription({ type: 'answer', sdp: await sdpRes.text() })
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e))
      setStatus('error')
      stop()
    }
  }

  function stop() {
    dcRef.current?.close()
    pcRef.current?.close()
    streamRef.current?.getTracks().forEach((t) => t.stop())
    dcRef.current = null
    pcRef.current = null
    streamRef.current = null
    if (status !== 'error') setStatus('idle')
  }

  return { status, error, start, stop }
}
