import AdminMenu from './AdminMenu'

// The study's own controls in the portal navbar. Apparatus, not portal actions.
//
// No Finish button here, deliberately. The participant leaves for Qualtrics from the outcome
// screen, once the arm has finished with them - a Finish control in the navbar would let someone
// skip the claim entirely and land in the emotion measures having felt nothing.
export default function StudyChrome() {
  return <AdminMenu />
}
