import { Badge, Button, Card } from 'react-bootstrap'
import { useVoiceSession, type VoiceStatus } from './useVoiceSession'
import { TASK_RAIL_WIDTH, VOICE_WIDGET_GAP } from '../study/layout'

const STATUS_VARIANT: Record<VoiceStatus, string> = {
  idle: 'secondary',
  connecting: 'warning',
  live: 'success',
  error: 'danger',
}

// Size of the panel at levels 1-2. Deliberately large: at these levels the assistant is the
// condition under study, so it has to read as a present participant in the task rather than a
// dismissible support bubble in the corner. The viewport clamp keeps it on screen on a small
// laptop, where a fixed 420 would otherwise crowd the portal it sits over.
const WIDTH = `min(420px, calc(100vw - ${TASK_RAIL_WIDTH + 2 * VOICE_WIDGET_GAP}px))`

// The panel is position:fixed, so it is placed against the viewport, not the portal pane it
// visually belongs to - left alone it would sit on top of the task rail. Offsetting by the rail's
// width parks it just inside the portal's column instead.
const RIGHT = TASK_RAIL_WIDTH + VOICE_WIDGET_GAP

export default function VoiceWidget() {
  // The session auto-starts on mount (see useVoiceSession); the participant never starts it.
  // The transcript is captured for the behavioural log but deliberately not shown here.
  const { status, error, start, stop } = useVoiceSession()
  const busy = status === 'connecting' || status === 'live'

  return (
    <Card
      className="shadow-lg"
      style={{ position: 'fixed', right: RIGHT, bottom: VOICE_WIDGET_GAP, width: WIDTH, zIndex: 1050 }}
    >
      <Card.Body className="p-4">
        <div className="d-flex justify-content-between align-items-center">
          <strong className="fs-5">Assistant</strong>
          <Badge bg={STATUS_VARIANT[status]} className="fs-6 px-3 py-2">
            {status}
          </Badge>
        </div>

        {error && <div className="text-danger mt-3">{error}</div>}

        {/* Manual control kept only as a fallback (e.g. to retry after an error). */}
        {busy ? (
          <Button size="lg" variant="outline-danger" className="w-100 mt-3" onClick={stop}>
            Stop
          </Button>
        ) : (
          <Button size="lg" className="w-100 mt-3" onClick={start}>
            Start voice
          </Button>
        )}
      </Card.Body>
    </Card>
  )
}
