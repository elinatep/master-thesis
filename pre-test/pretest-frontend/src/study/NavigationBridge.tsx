import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { clearDestination, pendingDestination, subscribe } from './navigation'

/**
 * Carries out redirects asked for from outside React — in practice, the study taking over once the
 * portal reports what happened to the claim.
 *
 * <p>Navigating from an effect rather than from the callback itself is what makes this reliable:
 * the portal's claim form reports the submission and then navigates to the new claim's page, so a
 * redirect issued inline would be overridden a moment later. An effect runs after that navigation
 * has committed, so the study's redirect is the one that stands.
 *
 * <p>Replaces rather than pushes: the claim form and the portal's claim page are behind the
 * participant now, and the back button should not walk them into the middle of a finished flow.
 */
export default function NavigationBridge() {
  const navigate = useNavigate()
  const [, force] = useState(0)

  useEffect(() => subscribe(() => force((n) => n + 1)), [])

  useEffect(() => {
    const destination = pendingDestination()
    if (!destination) return
    clearDestination()
    navigate(destination, { replace: true })
  })

  return null
}
