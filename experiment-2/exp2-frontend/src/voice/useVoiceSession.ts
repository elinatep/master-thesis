import { useEffect, useRef, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { getLevel } from '../study/level'
import { logEvent, logTranscript } from '../study/log'
import { participantHeaders } from '../study/headers'
import { getClientVoiceConfig } from './voiceConfig'
import {
  attachDocument,
  fillClaimForm,
  generateCertificate,
  openClaim,
  openClaims,
  openDashboard,
  openPolicies,
  openPolicy,
  openPolicyDocument,
  openPreferences,
  openProfile,
  openRequest,
  openRequests,
  registerNavigator,
  requestPolicyChange,
  sendClaimMessage,
  startClaim,
  submitClaimForm,
  updatePreferences,
  updateProfile,
} from '@insurance-portal/core/gui'

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

function parseArgs(s?: string): Record<string, unknown> {
  if (!s) return {}
  try {
    return JSON.parse(s) as Record<string, unknown>
  } catch {
    return {}
  }
}

function optNum(v: unknown): number | null {
  return v == null || v === '' ? null : Number(v)
}

function optStr(v: unknown): string | undefined {
  return v == null ? undefined : String(v)
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
  const navigate = useNavigate()

  // Level 2 drives the GUI through the guiBridge, which needs a navigate function. Register
  // ours while this session hook is mounted (it lives inside the router, so navigate is valid).
  useEffect(() => registerNavigator(navigate), [navigate])

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
   * Level 2: execute the call by driving the live portal GUI as the participant would.
   * Every tool here is a GUI verb — open a page, fill the form, submit it — handled by the
   * guiBridge, which navigates the real app and reads back only what a page has rendered. The
   * bot never calls the backend itself; backend access happens as the normal loading of the
   * pages it opens. (Contrast runViaBackend below, used by Level 3, which has no GUI.)
   */
  async function runViaGui(call: RealtimeOutputItem): Promise<unknown> {
    const args = parseArgs(call.arguments)
    switch (call.name) {
      case 'open_dashboard':
        return await openDashboard()
      case 'open_policies':
        return await openPolicies()
      case 'open_policy':
        return await openPolicy(String(args.policyNumber ?? ''))
      case 'open_claims':
        return await openClaims()
      case 'open_claim':
        return await openClaim(String(args.claimNumber ?? ''))
      case 'open_policy_document':
        return await openPolicyDocument(String(args.policyNumber ?? ''), optStr(args.filename))
      case 'open_profile':
        return await openProfile()
      case 'update_profile':
        return await updateProfile({
          email: optStr(args.email),
          phone: optStr(args.phone),
          street: optStr(args.street),
          postcode: optStr(args.postcode),
          city: optStr(args.city),
        })
      case 'open_preferences':
        return await openPreferences()
      case 'update_preferences':
        return await updatePreferences({
          correspondence: optStr(args.correspondence),
          language: optStr(args.language),
          marketingOptIn: 'marketingOptIn' in args ? Boolean(args.marketingOptIn) : undefined,
        })
      case 'start_claim':
        return await startClaim()
      case 'fill_claim_form':
        return await fillClaimForm({
          policyNumber: optStr(args.policyNumber),
          type: optStr(args.type),
          incidentDate: optStr(args.incidentDate),
          amount: 'amount' in args ? optNum(args.amount) : undefined,
          description: 'description' in args ? optStr(args.description) ?? null : undefined,
        })
      case 'submit_claim_form':
        return await submitClaimForm()
      case 'attach_document':
        return await attachDocument(
          String(args.claimNumber ?? ''),
          String(args.filename ?? ''),
          optStr(args.description),
        )
      case 'generate_certificate':
        return await generateCertificate(String(args.policyNumber ?? ''), String(args.type ?? ''))
      case 'send_claim_message':
        return await sendClaimMessage(String(args.claimNumber ?? ''), String(args.message ?? ''))
      case 'open_requests':
        return await openRequests()
      case 'open_request':
        return await openRequest(String(args.requestNumber ?? ''))
      case 'request_policy_change':
        return await requestPolicyChange(
          String(args.policyNumber ?? ''),
          String(args.changeType ?? ''),
          optStr(args.details),
        )
      default:
        return { error: `Unknown tool: ${String(call.name)}` }
    }
  }

  /** Level 3 (voice-only, no GUI): relay the call to the server-side tool dispatcher. */
  async function runViaBackend(call: RealtimeOutputItem): Promise<unknown> {
    const res = await fetch('/api/voice/tool', {
      method: 'POST',
      // Scope the bot's server-side tools to this participant's data, like the GUI calls.
      headers: { 'Content-Type': 'application/json', ...participantHeaders() },
      body: JSON.stringify({ name: call.name, arguments: call.arguments ?? '{}' }),
    })
    // Backend always answers 200 with the result or an {error} object; relay either way.
    return res.ok ? await res.json() : { error: `Tool endpoint failed (${res.status})` }
  }

  /** Execute one bot function call and hand the result back as function_call_output. */
  async function executeAndReply(call: RealtimeOutputItem) {
    if (!call.name || !call.call_id) return
    console.debug('[ui] tool call:', call.name, 'args=', call.arguments, '(level', getLevel() + ')')
    logEvent('VOICE_TOOL_CALL', { name: call.name, args: call.arguments ?? null })
    let result: unknown
    try {
      result = getLevel() === 2 ? await runViaGui(call) : await runViaBackend(call)
    } catch (e) {
      result = { error: e instanceof Error ? e.message : String(e) }
    }
    console.debug('[ui] tool result:', call.name, '->', result)
    // Log the outcome compactly: whether it errored, not the full payload.
    const errored = typeof result === 'object' && result !== null && 'error' in result
    logEvent('VOICE_TOOL_RESULT', { name: call.name, ok: !errored, error: errored ? String((result as { error: unknown }).error) : null })
    send({
      type: 'conversation.item.create',
      item: { type: 'function_call_output', call_id: call.call_id, output: JSON.stringify(result) },
    })
  }

  function handleEvent(evt: RealtimeEvent) {
    // Verbose for now; Phase 4 will persist transcripts to the behavioural schema.
    console.debug('[voice]', evt.type, evt)

    // Function calls arrive on the completed response. Execute them ONE AT A TIME — Level 2
    // drives a single live GUI (one page, one form), so concurrent calls would collide on
    // the shared navigation/form channel; sequential also mirrors a human operating the
    // screen. Then ask the model to continue (response.create) so it speaks the outcome.
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
      // Pass the assigned level so the backend applies that level's instructions.
      const tokenRes = await fetch(`/api/voice/token?level=${getLevel()}`, { method: 'POST' })
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
        // model would otherwise stay silent until spoken to. Identical across levels (parity).
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
