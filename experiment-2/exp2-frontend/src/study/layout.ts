// Width of the task rail (StudySplit), in pixels.
//
// Shared, because two unrelated things have to agree on it: the grid column the rail occupies, and
// VoiceWidget's `right` offset — the widget is position:fixed, so it is placed against the viewport
// and would otherwise sit on top of the rail. One constant, so moving the rail cannot silently
// bury the assistant panel underneath it.
export const TASK_RAIL_WIDTH = 340

/** Gap kept between the assistant panel and the rail / the bottom of the screen. */
export const VOICE_WIDGET_GAP = 24
