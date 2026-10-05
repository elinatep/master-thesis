// The participant's task, fetched from the backend rather than bundled.
//
// It is the stimulus: it has to match, word for word, what Qualtrics told the participant on the
// screen immediately before the handover. Wording compiled into this bundle could only be corrected
// by a rebuild, a republish and a redeploy - too slow to keep in step with a survey being edited,
// and too easy to leave stale. See TaskController for the other half of that argument.

/** One rendered piece of the task card. Deliberately a tiny subset of Markdown - see parseTask. */
export type TaskBlock =
  | { kind: 'heading'; text: string }
  | { kind: 'paragraph'; spans: TaskSpan[] }
  | { kind: 'bullets'; items: TaskSpan[][] }

/** Bold is the only inline mark: it is what marks the numbers the participant has to carry. */
export interface TaskSpan {
  text: string
  bold: boolean
}

let cached: Promise<TaskBlock[]> | null = null

/**
 * Fetch and parse the task. Memoised: the card is remounted whenever the participant moves between
 * portal pages, and refetching the same unchanging text on every navigation would put a request in
 * the log for something the participant did not do.
 */
export function loadTask(): Promise<TaskBlock[]> {
  if (!cached) {
    cached = fetch('/api/study/task')
      .then((res) => {
        if (!res.ok) throw new Error(`task ${res.status}`)
        return res.text()
      })
      .then(parseTask)
  }
  return cached
}

/**
 * The Markdown subset the task file may use: a `# ` heading, `- ` bullets, blank-line-separated
 * paragraphs, and `**bold**`.
 *
 * <p>Deliberately not a Markdown library. The task file is written by the researcher, not by a
 * participant, so the input is trusted and tiny - and the alternative is either a dependency whose
 * output has to be sanitised before it goes near dangerouslySetInnerHTML, or a card that renders
 * raw asterisks at a participant mid-study. Anything this parser does not recognise degrades to
 * plain text rather than failing.
 */
export function parseTask(markdown: string): TaskBlock[] {
  const blocks: TaskBlock[] = []
  let bullets: TaskSpan[][] = []

  const flushBullets = () => {
    if (bullets.length) {
      blocks.push({ kind: 'bullets', items: bullets })
      bullets = []
    }
  }

  // Paragraphs may be wrapped across lines in the source file, so lines are joined until a blank
  // one - otherwise a sentence wrapped at 100 characters renders as several stunted paragraphs.
  let paragraph: string[] = []
  const flushParagraph = () => {
    if (paragraph.length) {
      blocks.push({ kind: 'paragraph', spans: parseSpans(paragraph.join(' ')) })
      paragraph = []
    }
  }

  for (const raw of markdown.split('\n')) {
    const line = raw.trim()
    if (!line) {
      flushParagraph()
      flushBullets()
    } else if (line.startsWith('# ')) {
      flushParagraph()
      flushBullets()
      blocks.push({ kind: 'heading', text: line.slice(2).trim() })
    } else if (line.startsWith('- ')) {
      flushParagraph()
      bullets.push(parseSpans(line.slice(2).trim()))
    } else {
      flushBullets()
      paragraph.push(line)
    }
  }
  flushParagraph()
  flushBullets()
  return blocks
}

/** Split on `**bold**`. An unclosed `**` stays literal rather than swallowing the rest of the line. */
function parseSpans(text: string): TaskSpan[] {
  const spans: TaskSpan[] = []
  const pattern = /\*\*(.+?)\*\*/g
  let last = 0
  let match: RegExpExecArray | null
  while ((match = pattern.exec(text)) !== null) {
    if (match.index > last) spans.push({ text: text.slice(last, match.index), bold: false })
    spans.push({ text: match[1], bold: true })
    last = match.index + match[0].length
  }
  if (last < text.length) spans.push({ text: text.slice(last), bold: false })
  return spans
}
