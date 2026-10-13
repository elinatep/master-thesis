import { useEffect, useState } from 'react'
import CallStage from './CallStage'
import { getStage, subscribeStage } from '../study/stage'

/**
 * The voice surface, present only once the participant has asked for a review.
 *
 * This is the fix for the thing that was plainly wrong: the assistant used to mount with the
 * portal and open a session immediately, so it started talking to someone who had not yet filed a
 * claim, let alone seen an assessment to dispute. The flow is linear - claim, assessment, decision,
 * call - and the assistant belongs in the last step only.
 *
 * It renders over the portal rather than replacing it, deliberately. The call is about the claim
 * they just filed, the agent's script has them refer to specific details of it, and a participant
 * who wants to look at their own policy while talking about it should be able to.
 */
export default function AssistantStage() {
  const [stage, setStage] = useState(getStage)

  useEffect(() => {
    // Also listen for the storage event, so the stage is right if the page was reloaded between
    // the decision and this mount.
    const sync = () => setStage(getStage())
    const unsubscribe = subscribeStage(sync)
    window.addEventListener('salvena:stage', sync)
    sync()
    return () => {
      unsubscribe()
      window.removeEventListener('salvena:stage', sync)
    }
  }, [])

  if (stage !== 'call') return null
  return <CallStage />
}
