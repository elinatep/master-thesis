// Which leg of the study the participant is on, within one platform visit.
//
// The flow is linear: file the claim in the portal, read the assessment, and then either accept it
// and go back to Qualtrics or ask customer service to review it - which is where the AI assistant
// and then the human employee come in.
//
// So the voice surface must NOT be present while they are filing the claim. It used to mount with
// the portal and open a session immediately, which meant an assistant talking to someone who had
// not yet seen anything to dispute.
//
// Held in sessionStorage rather than in React state: the call survives a reload, and a participant
// who refreshes mid-call comes back to the call rather than to the claim form. Per-tab, cleared
// when the tab closes, and never read by the server - the server learns about the choice from the
// behavioural log like everything else.

export type Stage = 'task' | 'call'

const KEY = 'salvena.stage'
const EVENT = 'salvena:stage'

type Listener = () => void
const listeners = new Set<Listener>()

export function getStage(): Stage {
  try {
    return sessionStorage.getItem(KEY) === 'call' ? 'call' : 'task'
  } catch {
    // Private mode, or storage blocked. Falling back to 'task' is the safe direction: the
    // participant sees the portal rather than a call with nobody on it.
    return 'task'
  }
}

export function setStage(stage: Stage): void {
  try {
    if (stage === 'task') sessionStorage.removeItem(KEY)
    else sessionStorage.setItem(KEY, stage)
  } catch {
    // Nothing to do: the in-memory listeners below still fire, so the current page responds. Only
    // a reload would lose it.
  }
  listeners.forEach((l) => l())
  window.dispatchEvent(new Event(EVENT))
}

export function subscribeStage(listener: Listener): () => void {
  listeners.add(listener)
  return () => listeners.delete(listener)
}

const SESSION_KEY = 'salvena.stage.session'

/**
 * Point the stage at the session that has just been opened, resetting it if this is a new run.
 *
 * <p>Two cases have to be told apart, and the session id is what tells them apart.
 *
 * <p>A <b>reload</b> re-opens the same session (the backend hands the existing one back), and the
 * participant must come back to where they were - a refresh during the call returns to the call
 * rather than to a claim form they have already filled in.
 *
 * <p>A <b>new participant in the same tab</b> gets a different session. sessionStorage is per tab
 * and survives a navigation, so without this they would inherit the previous participant's stage:
 * the assessment would open already "in a call", they would never be offered the choice, and
 * ESCALATION_CHOICE would never be logged for them. Unlikely with one participant per tab, and
 * routine while piloting.
 */
export function syncStageToSession(sessionId: number): void {
  try {
    const previous = sessionStorage.getItem(SESSION_KEY)
    const current = String(sessionId)
    if (previous !== current) {
      sessionStorage.setItem(SESSION_KEY, current)
      if (previous !== null) setStage('task')
    }
  } catch {
    // Storage blocked. The stage falls back to 'task' on read, which is the safe direction.
  }
}
