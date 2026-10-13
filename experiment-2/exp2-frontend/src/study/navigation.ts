// Lets the study redirect the participant from outside React.
//
// The portal reports what the participant did through a plain callback (PortalHost.onEvent), with
// no router in scope, so the study learns a claim was filed with nothing to navigate with.
//
// Calling a navigate function from there is not enough on its own. The portal's claim form reports
// the submission and THEN navigates to the new claim's page, so a navigation issued from inside
// the callback is immediately overridden by the portal's own. Instead the callback raises a
// request, and a component inside the router acts on it from an effect - effects run after the
// portal's navigation has committed, so the study's redirect is the one that lands. Ordering by
// React's own guarantees rather than by a timer, which would be a race.

type Listener = () => void

let pending: string | null = null
const listeners = new Set<Listener>()

/** Ask to be taken somewhere. Safe to call from outside React; acted on by the watcher. */
export function goTo(path: string): void {
  pending = path
  listeners.forEach((l) => l())
}

/** The outstanding request, if any. */
export function pendingDestination(): string | null {
  return pending
}

/** Called by the watcher once it has navigated, so a later render does not repeat the redirect. */
export function clearDestination(): void {
  pending = null
}

export function subscribe(listener: Listener): () => void {
  listeners.add(listener)
  return () => listeners.delete(listener)
}
