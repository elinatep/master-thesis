import { Button } from 'react-bootstrap'
import AdminMenu from './AdminMenu'
import { useHandback } from './useHandback'

// The study's own controls, rendered into the portal navbar through the host's chrome slot.
// Apparatus, not portal actions — which is why they stay interactive even in read-only mode, where
// everything portal-facing is inert: the participant must always be able to finish the session, and
// the researcher to reach the reset tools.

export default function StudyChrome() {
  const { finishing, finish } = useHandback()
  return (
    <>
      <AdminMenu />
      <Button size="sm" variant="success" disabled={finishing} onClick={finish}>
        {finishing ? 'Finishing…' : 'Finish'}
      </Button>
    </>
  )
}
