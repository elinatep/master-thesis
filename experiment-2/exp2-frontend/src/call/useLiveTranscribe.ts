import { useEffect, useRef } from 'react'
import { logEvent } from '../study/log'

/** What the server says about transcription, minus anything secret. */
interface TranscribeConfig {
  enabled: boolean
  participantSide: boolean
  socketUrl: string
  subprotocolPrefix?: string
  sampleRate?: number
}

interface Options {
  /** Transcribe only while this is true - in practice, only while the call is live. */
  active: boolean
  /** Where to mint the ephemeral token. Differs by side; both are guarded, neither is public. */
  tokenUrl: string
  tokenHeaders?: () => Record<string, string>
  /** Where a finished line goes. The two sides persist differently, so neither is assumed here. */
  onLine: (text: string) => void
}

/**
 * Transcribes this browser's own microphone while the human call is live.
 *
 * <p>The AI leg transcribes itself - Realtime is already listening - but the human leg is two
 * browsers on a Communication Services line with nothing listening at all. So the half of the call
 * the study actually manipulates has, until now, had no record of what was said: whether the
 * acknowledgement was delivered on a given call has been something the agent is asked to remember.
 *
 * Each side transcribes its OWN microphone rather than the mixed call audio. That keeps the speaker
 * labels honest without diarisation, and it means each side only ever sends its own voice.
 *
 * No audio is stored, here or anywhere: the samples go to the recogniser and the finished text
 * comes back. Same commitment the AI leg already keeps.
 *
 * **Nothing in here may break the call.** Every failure is logged and swallowed. A participant
 * whose call drops because the transcription socket refused is a data point lost to instrumentation,
 * which is worse than having no instrumentation.
 */
export function useLiveTranscribe({ active, tokenUrl, tokenHeaders, onLine }: Options) {
  // Everything that has to be torn down, in one place: a half-closed socket holding a microphone
  // open is what leaves the recording indicator lit after the call has ended.
  const stopRef = useRef<(() => void) | null>(null)
  const onLineRef = useRef(onLine)
  onLineRef.current = onLine

  useEffect(() => {
    if (!active) return
    let cancelled = false

    const start = async () => {
      let socket: WebSocket | null = null
      let stream: MediaStream | null = null
      let context: AudioContext | null = null

      const stop = () => {
        try { socket?.close() } catch { /* already gone */ }
        try { stream?.getTracks().forEach((t) => t.stop()) } catch { /* already gone */ }
        try { void context?.close() } catch { /* already gone */ }
        socket = null
        stream = null
        context = null
      }
      stopRef.current = stop

      try {
        const config: TranscribeConfig = await (await fetch('/api/call/transcribe/config')).json()
        if (!config.enabled || cancelled) return

        const res = await fetch(tokenUrl, { method: 'POST', headers: tokenHeaders?.() })
        if (!res.ok) {
          // 503 is "not configured" and 403 is "switched off for this side". Neither is a fault,
          // and neither should fill the log with errors on every call.
          if (res.status !== 503 && res.status !== 403) {
            logEvent('TRANSCRIBE_ERROR', { stage: 'token', status: res.status })
          }
          return
        }
        const token: string = (await res.json())?.value
        if (!token || cancelled) return

        const rate = config.sampleRate ?? 24000
        stream = await navigator.mediaDevices.getUserMedia({ audio: true })
        if (cancelled) { stop(); return }

        socket = new WebSocket(config.socketUrl, [
          'realtime',
          (config.subprotocolPrefix ?? 'openai-insecure-api-key.') + token,
        ])
        const ws = socket

        ws.onerror = () => logEvent('TRANSCRIBE_ERROR', { stage: 'socket' })
        ws.onmessage = (e) => {
          try {
            const evt = JSON.parse(String(e.data))
            // Finals only. The deltas arrive continuously and would make the transcript table a
            // log of keystrokes; the completed event carries the whole turn.
            const type = String(evt?.type ?? '')
            if (type.includes('input_audio_transcription')
                && (type.endsWith('completed') || type.endsWith('done'))
                && typeof evt.transcript === 'string' && evt.transcript.trim()) {
              onLineRef.current(evt.transcript.trim())
            }
            if (type === 'error') {
              // The service's own complaint, verbatim. Written against the documentation rather
              // than a live endpoint, so this is how a wrong session shape becomes visible.
              logEvent('TRANSCRIBE_ERROR', { stage: 'session', detail: evt?.error })
            }
          } catch {
            // A frame we cannot parse is not worth ending the call over.
          }
        }

        context = new AudioContext({ sampleRate: rate })
        const source = context.createMediaStreamSource(stream)
        // ScriptProcessor is deprecated in favour of AudioWorklet, which needs a separate module
        // file served alongside the bundle. Not worth it for one mono PCM conversion that runs for
        // a few minutes a call; if it is ever removed, this is the thing that breaks.
        const node = context.createScriptProcessor(4096, 1, 1)
        node.onaudioprocess = (e) => {
          if (ws.readyState !== WebSocket.OPEN) return
          const input = e.inputBuffer.getChannelData(0)
          const pcm = new Int16Array(input.length)
          for (let i = 0; i < input.length; i++) {
            const s = Math.max(-1, Math.min(1, input[i]))
            pcm[i] = s < 0 ? s * 0x8000 : s * 0x7fff
          }
          let binary = ''
          const bytes = new Uint8Array(pcm.buffer)
          for (let i = 0; i < bytes.length; i++) binary += String.fromCharCode(bytes[i])
          ws.send(JSON.stringify({ type: 'input_audio_buffer.append', audio: btoa(binary) }))
        }
        source.connect(node)
        // Through a silenced gain node: a ScriptProcessor only runs while it is connected to the
        // destination, and connecting it directly would play the agent's own microphone back into
        // the call.
        const silence = context.createGain()
        silence.gain.value = 0
        node.connect(silence)
        silence.connect(context.destination)

        if (cancelled) stop()
      } catch (e) {
        logEvent('TRANSCRIBE_ERROR', {
          stage: 'start',
          error: e instanceof Error ? e.message : String(e),
        })
        stop()
      }
    }

    void start()
    return () => {
      cancelled = true
      stopRef.current?.()
      stopRef.current = null
    }
  }, [active, tokenUrl, tokenHeaders])
}
