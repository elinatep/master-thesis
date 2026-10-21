import { useCallback, useEffect, useRef, useState } from 'react'
import type { Call, CallAgent } from '@azure/communication-calling'
import { logEvent } from '../study/log'

/**
 * One side of the participant-to-agent voice call, on Azure Communication Services.
 *
 * Both sides use this hook. They differ only in which endpoint mints their token - the participant
 * gets their own call from `POST /api/call/token`, the agent gets a named handover's from
 * `POST /api/agent/handover/{id}/call-token` - and in the display name they join under.
 *
 * The audio never passes through our server: it mints a short-lived token, and the two browsers
 * join a group call whose id is the handover's. Nothing is recorded anywhere; the ethics
 * application forbids storing voice data, and the server not being in the media path is the
 * simplest way to be sure of it.
 */
export type CallStatus =
  /** Nothing started yet. */
  | 'idle'
  /** Getting a token, asking for the microphone, joining. */
  | 'connecting'
  /** In the call, alone. For the participant this is hold; for the agent it means they beat them in. */
  | 'waiting'
  /** Both sides are in. */
  | 'live'
  /** Over - hung up by either side. */
  | 'ended'
  | 'error'

interface Credentials {
  userId: string
  token: string
  expiresOn: string
  callId: string
}

export interface AgentCallOptions {
  /** Where to POST for a token. Differs between the two sides; nothing else does. */
  tokenUrl: string
  /**
   * Headers the token request needs to say who is asking.
   *
   * <p>The two sides identify themselves differently, and this is where that difference lives. The
   * agent is identified by the handover id in the URL and by their login, so they need none. The
   * participant is identified by X-Participant-Id, like every other participant-facing call - and
   * without it the server cannot tell whose call to issue a token for, which is a 400 and a
   * participant who sits watching "connecting" forever.
   */
  tokenHeaders?: () => Record<string, string>
  /** Shown to the other side in the call. Not a study variable - see the note in CallStage. */
  displayName: string
  /** Join as soon as the hook mounts. The participant does; the agent clicks. */
  autoJoin?: boolean
}

export function useAgentCall({ tokenUrl, displayName, tokenHeaders, autoJoin = false }: AgentCallOptions) {
  const [status, setStatus] = useState<CallStatus>('idle')
  const [error, setError] = useState<string | null>(null)
  const [muted, setMuted] = useState(false)
  const [othersPresent, setOthersPresent] = useState(0)

  const agentRef = useRef<CallAgent | null>(null)
  const callRef = useRef<Call | null>(null)
  // Guards the double-invocation of effects under React StrictMode in dev. Without it the
  // participant joins the same call twice and hears themselves.
  const startedRef = useRef(false)

  const leave = useCallback(async () => {
    const call = callRef.current
    const agent = agentRef.current
    callRef.current = null
    agentRef.current = null
    try {
      if (call && call.state !== 'Disconnected') await call.hangUp()
    } catch {
      // Already gone. Hanging up a call that has ended is not a failure worth surfacing to
      // somebody mid-session.
    }
    try {
      await agent?.dispose()
    } catch {
      // Same.
    }
  }, [])

  const join = useCallback(async () => {
    if (startedRef.current) return
    startedRef.current = true
    setError(null)
    setStatus('connecting')

    try {
      const res = await fetch(tokenUrl, { method: 'POST', headers: tokenHeaders?.() })
      if (res.status === 503) {
        throw new Error('Calling is not configured on this deployment')
      }
      if (res.status === 409) {
        throw new Error('There is no call to join yet')
      }
      if (res.status === 400) {
        // The server could not tell who is asking. Named rather than folded into the generic
        // message below, because the generic one ("Could not get a call token (400)") says nothing
        // about the cause and this failure is silent on the server otherwise.
        throw new Error('The call request did not identify the participant')
      }
      if (!res.ok) {
        throw new Error(`Could not get a call token (${res.status})`)
      }
      const creds = (await res.json()) as Credentials
      if (!creds.token || !creds.callId) {
        throw new Error('The call token response was incomplete')
      }

      // Loaded here rather than imported at the top, and this is worth the awkwardness: the
      // Calling SDK is about 1.5 MB of the bundle, and nothing needs it until somebody asks for a
      // review. Every participant was downloading it before they had even filed a claim, on their
      // own connection, and most of a page load is the first thing they judge the service on.
      //
      // The type-only import above stays static, so the types still check; only the runtime cost
      // moves. By the time this runs the participant has read an assessment and clicked a button,
      // which is several seconds of network head start.
      const [{ CallClient }, { AzureCommunicationTokenCredential }] = await Promise.all([
        import('@azure/communication-calling'),
        import('@azure/communication-common'),
      ])

      const client = new CallClient()

      // Ask for the microphone before joining rather than letting the join trigger the prompt.
      // The participant is on their own machine and has just been told a colleague is coming: a
      // permission dialog appearing underneath a "connecting" spinner is how a call gets missed.
      const devices = await client.getDeviceManager()
      const access = await devices.askDevicePermission({ audio: true, video: false })
      if (!access.audio) {
        throw new Error('Microphone permission was refused')
      }

      const credential = new AzureCommunicationTokenCredential(creds.token)
      const callAgent = await client.createCallAgent(credential, { displayName })
      agentRef.current = callAgent

      const call = callAgent.join({ groupId: creds.callId }, { audioOptions: { muted: false } })
      callRef.current = call

      const onState = () => {
        if (call.state === 'Connected') {
          // 'waiting' vs 'live' is about whether anyone else is here, not about the connection.
          setStatus(call.remoteParticipants.length > 0 ? 'live' : 'waiting')
        } else if (call.state === 'Disconnected') {
          setStatus('ended')
        }
      }
      const onParticipants = () => {
        const n = call.remoteParticipants.length
        setOthersPresent(n)
        if (call.state === 'Connected') setStatus(n > 0 ? 'live' : 'waiting')
      }
      const onMuted = () => setMuted(call.isMuted)

      call.on('stateChanged', onState)
      call.on('remoteParticipantsUpdated', onParticipants)
      call.on('isMutedChanged', onMuted)
      // The events above fire on change; read the current values once in case joining already
      // settled before the listeners were attached.
      onState()
      onParticipants()
      onMuted()
    } catch (e) {
      const message = e instanceof Error ? e.message : String(e)
      // Into the behavioural log, not just onto the screen. The first live run failed here - the
      // participant's token request was rejected - and the only trace of it was a line in the
      // server log that nobody read for a day. Across four hundred sessions, how many calls failed
      // and why is a number the study needs, and it has to come from the data rather than from
      // somebody noticing.
      logEvent('CALL_ERROR', { error: message })
      setError(message)
      setStatus('error')
      // Let them try again: the commonest causes - a refused microphone, a token that arrived
      // late - are both things a second attempt fixes.
      startedRef.current = false
      void leave()
    }
  }, [tokenUrl, displayName, tokenHeaders, leave])

  const hangUp = useCallback(async () => {
    await leave()
    setStatus('ended')
  }, [leave])

  const toggleMute = useCallback(async () => {
    const call = callRef.current
    if (!call) return
    if (call.isMuted) await call.unmute()
    else await call.mute()
    setMuted(call.isMuted)
  }, [])

  // Every transition, the same way the AI leg records VOICE_STATUS. Together they show how long a
  // participant waited on hold and whether the two legs actually joined up.
  //
  // A no-op in the agent's console: logEvent does nothing without a participant session, and the
  // console has none.
  useEffect(() => {
    logEvent('CALL_STATUS', { status })
  }, [status])

  useEffect(() => {
    if (autoJoin) void join()
  }, [autoJoin, join])

  // Leaving the page must end the call. Without this the browser holds the microphone and the
  // other side is left talking to a connection nobody is behind.
  useEffect(() => {
    return () => {
      void leave()
    }
  }, [leave])

  return { status, error, muted, othersPresent, join, hangUp, toggleMute }
}
