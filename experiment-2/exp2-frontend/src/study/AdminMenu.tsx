import { useState, useSyncExternalStore } from 'react'
import { Button, Modal } from 'react-bootstrap'
import { isAdminVisible, subscribeAdminVisible } from './adminVisibility'
import {
  resetAllData,
  resetCurrentUserAll,
  resetCurrentUserBehavioural,
  resetCurrentUserBusiness,
} from './adminApi'
import { getHandoverToken } from './session'

// Researcher tooling: a navbar button opening a popup of data-reset actions. After any reset we
// re-enter the portal exactly as a fresh Qualtrics handover would — a full page reload to / carrying
// the same ?t= (reusable within its TTL), so the token is re-exchanged and provisioning/seeding
// runs again cleanly.

/** Full reload to / carrying the handover token, i.e. a fresh participant entry. */
function reenter() {
  const token = getHandoverToken()
  window.location.href = token ? `/?t=${encodeURIComponent(token)}` : '/'
}

export default function AdminMenu() {
  const visible = useSyncExternalStore(subscribeAdminVisible, isAdminVisible)
  const [show, setShow] = useState(false)
  const [busy, setBusy] = useState(false)

  async function run(action: () => Promise<void>, confirmMessage: string) {
    if (!window.confirm(confirmMessage)) return
    setBusy(true)
    try {
      await action()
      reenter() // full reload; component unmounts, no need to clear busy
    } catch (e) {
      setBusy(false)
      window.alert('Reset failed: ' + (e instanceof Error ? e.message : String(e)))
    }
  }

  if (!visible) return null // hidden by default; reveal from the console with showAdmin()

  return (
    <>
      <Button size="sm" variant="outline-light" onClick={() => setShow(true)}>
        Admin
      </Button>

      <Modal show={show} onHide={() => !busy && setShow(false)} centered>
        <Modal.Header closeButton>
          <Modal.Title>Admin — reset data</Modal.Title>
        </Modal.Header>
        <Modal.Body className="d-grid gap-2">
          <Button
            variant="danger"
            disabled={busy}
            onClick={() => run(resetAllData, 'Reset ALL business and behavioural data for ALL participants? This cannot be undone.')}
          >
            Reset all data (all users)
          </Button>
          <Button
            variant="warning"
            disabled={busy}
            onClick={() => run(resetCurrentUserAll, 'Reset all data (business + behavioural) for the current participant?')}
          >
            Reset all data of current user
          </Button>
          <Button
            variant="outline-secondary"
            disabled={busy}
            onClick={() => run(resetCurrentUserBusiness, 'Re-seed the current participant’s business data (policies, claims, documents)?')}
          >
            Reset business data of current user
          </Button>
          <Button
            variant="outline-secondary"
            disabled={busy}
            onClick={() => run(resetCurrentUserBehavioural, 'Delete the current participant’s behavioural logs?')}
          >
            Reset behavioural data of current user
          </Button>
        </Modal.Body>
        <Modal.Footer>
          <span className="text-muted small me-auto">Reloads the portal fresh afterwards.</span>
          <Button variant="secondary" disabled={busy} onClick={() => setShow(false)}>
            Close
          </Button>
        </Modal.Footer>
      </Modal>
    </>
  )
}
