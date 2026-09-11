// The participant's task, fetched from the backend (configuration/study/task.md) rather than
// bundled — see TaskController for why the wording has to be editable without a rebuild.

export interface Task {
  title: string
  paragraphs: string[]
}

/**
 * The Markdown subset the task card renders: one leading `# ` heading, then blank-line-separated
 * paragraphs. Deliberately not a Markdown library — the file is written by us for exactly one
 * card, and a parser here is both smaller than the dependency and impossible to get surprising
 * output from (no injected HTML, no half-supported syntax).
 *
 * Single newlines inside a paragraph are soft-wrapped away, so the file can stay within a sane
 * line length without that turning into ragged line breaks on screen.
 */
export function parseTask(markdown: string): Task {
  const blocks = markdown
    .replace(/\r\n/g, '\n')
    .split(/\n\s*\n/)
    .map((b) => b.trim())
    .filter(Boolean)

  let title = 'Your task'
  let titleTaken = false
  const paragraphs: string[] = []
  for (const block of blocks) {
    // Only the first block may be the heading. Tracked with a flag rather than by comparing against
    // the default, or a file whose heading happens to read "Your task" would let a second heading
    // overwrite it.
    if (!titleTaken && paragraphs.length === 0 && block.startsWith('# ')) {
      title = block.slice(2).trim()
      titleTaken = true
    } else {
      paragraphs.push(block.replace(/\s*\n\s*/g, ' '))
    }
  }
  return { title, paragraphs }
}

export async function fetchTask(): Promise<Task> {
  const res = await fetch('/api/study/task')
  if (!res.ok) throw new Error(`${res.status} ${res.statusText}`)
  return parseTask(await res.text())
}
