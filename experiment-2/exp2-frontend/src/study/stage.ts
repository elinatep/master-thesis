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
