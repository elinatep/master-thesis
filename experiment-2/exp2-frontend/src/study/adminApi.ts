// The study's admin endpoints (see AdminController).
//
// These used to live in the portal's API client, back when the portal and the study shared one
// build. They call /api/admin/*, which is the study's surface and not the portal's, so they belong
// here - and the portal dropped them when it moved to its own repository.
//
// hostHeaders() is the portal's: it returns whatever the host configured through
// configurePortalHost, which for us is the participant header. Using it rather than
// participantHeaders() directly keeps these calls identical to every other portal call.
import { hostHeaders } from '@insurance-portal/core/host'

async function adminPost(path: string): Promise<void> {
  const res = await fetch(path, { method: 'POST', headers: hostHeaders() })
  if (!res.ok) throw new Error(`${res.status} ${res.statusText}`)
}

/** Wipe ALL business + behavioural data for ALL participants. */
export function resetAllData(): Promise<void> {
  return adminPost('/api/admin/reset/all')
}

/** Reset the current participant's business (re-seed) and behavioural (delete logs) data. */
export function resetCurrentUserAll(): Promise<void> {
  return adminPost('/api/admin/reset/me')
}

/** Re-seed the current participant's business data only. */
export function resetCurrentUserBusiness(): Promise<void> {
  return adminPost('/api/admin/reset/me/business')
}

/** Delete the current participant's behavioural logs only. */
export function resetCurrentUserBehavioural(): Promise<void> {
  return adminPost('/api/admin/reset/me/behavioural')
}
