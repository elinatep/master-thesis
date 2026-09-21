// Admin button visibility. Hidden by default so participants never see it; the researcher
// reveals it from the DevTools console with showAdmin() (and hideAdmin() to put it away).
// Persisted in localStorage so it survives the full-page reloads the reset actions trigger,
// until explicitly hidden. The console controls exist in every build (the researcher flips it
// on the study laptop), but the button stays hidden unless toggled on.

const KEY = 'salvena.admin.visible'
const EVENT = 'salvena:admin-visibility'

export function isAdminVisible(): boolean {
  return localStorage.getItem(KEY) === '1'
}

function setAdminVisible(visible: boolean): void {
  if (visible) localStorage.setItem(KEY, '1')
  else localStorage.removeItem(KEY)
  window.dispatchEvent(new Event(EVENT))
  console.debug('[admin] button visible:', visible)
}

export function subscribeAdminVisible(onChange: () => void): () => void {
  window.addEventListener(EVENT, onChange)
  return () => window.removeEventListener(EVENT, onChange)
}

// Console controls: type showAdmin() / hideAdmin() in DevTools to toggle the Admin button.
Object.assign(window, {
  showAdmin: () => setAdminVisible(true),
  hideAdmin: () => setAdminVisible(false),
})
