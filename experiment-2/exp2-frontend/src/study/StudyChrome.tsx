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
      {/*
        escalated: false, explicitly. Whoever leaves by this button did not go through the call -
        they are finishing during the portal task - and Qualtrics gets a defined value for every
        participant rather than having to treat a missing field as a third case.
      */}
      <Button
        size="sm"
        variant="success"
        disabled={finishing}
        onClick={() => void finish({ escalated: false })}
      >
        {finishing ? 'Finishing…' : 'Finish'}
      </Button>
    </>
  )
}
