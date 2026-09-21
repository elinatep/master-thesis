import { useEffect, useMemo, useState } from 'react'
import { Alert, Badge, Button, Container, Form, Spinner, Table } from 'react-bootstrap'

// Behavioural-data admin/export view. Loads the full flat log (sessions + events + transcripts)
// from /api/study/log and presents it as a tree: one row per study session, expandable to reveal
// that session's events and transcript lines in chronological order. Search filters across all
// columns; CSV export dumps the full (searched) flat result set. Lives outside the participant
// gate (see StudyGate), so opening it creates no study session.

interface LogRow {
  kind: string
  sessionId: number | null
  participantId: string | null
  arm: string | null
  seq: number | null
  type: string
  detail: string | null
  /** CLIENT (browser) or SERVER (portal domain service) for events; null for sessions/transcripts. */
  source: string | null
  clientTs: string | null
  serverTs: string | null
}

interface SessionNode {
  sessionId: number | null
  participantId: string | null
  arm: string | null
  session: LogRow | null // the SESSION row itself (metadata), if present
  children: LogRow[] // events + transcripts, chronological
}

// Columns used for the flat CSV export. `arm` is the grouping variable for every comparison the
// pre-test makes, so it sits next to the participant.
const CSV_COLUMNS: (keyof LogRow)[] = [
  'serverTs', 'clientTs', 'participantId', 'arm', 'sessionId', 'kind', 'source', 'type', 'seq', 'detail',
]

function rowTs(r: LogRow): string {
  return r.clientTs ?? r.serverTs ?? ''
}

function rowMatches(r: LogRow, q: string): boolean {
  return [r.kind, r.type, r.detail, r.source, r.participantId, r.arm, r.seq, r.clientTs, r.serverTs]
    .some((v) => String(v ?? '').toLowerCase().includes(q))
}

function csvCell(v: unknown): string {
  const s = v == null ? '' : String(v)
  // Quote if it contains a comma, quote or newline; double up embedded quotes.
  return /[",\n]/.test(s) ? `"${s.replace(/"/g, '""')}"` : s
}

/** Pretty-print a JSON detail string; fall back to the raw text if it isn't JSON. */
function formatDetail(detail: string | null): string {
  if (!detail) return ''
  const t = detail.trim()
  if (t.startsWith('{') || t.startsWith('[')) {
    try {
      return JSON.stringify(JSON.parse(t), null, 2)
    } catch {
      /* not JSON, fall through */
    }
  }
  return detail
}

export default function DataPage() {
  const [rows, setRows] = useState<LogRow[] | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [search, setSearch] = useState('')
  const [expanded, setExpanded] = useState<Set<string>>(new Set())

  useEffect(() => {
    fetch('/api/study/log')
      .then((r) => {
        if (!r.ok) throw new Error(`${r.status} ${r.statusText}`)
        return r.json() as Promise<LogRow[]>
      })
      .then(setRows)
      .catch((e: unknown) => setError(e instanceof Error ? e.message : String(e)))
  }, [])

  const q = search.trim().toLowerCase()

  // Flat list filtered by the search query — drives CSV export and feeds the tree.
  const filteredRows = useMemo(() => {
    if (!rows) return []
    return q ? rows.filter((r) => rowMatches(r, q)) : rows
  }, [rows, q])

  // Group into session nodes. A session is shown when it (or any of its children) matched.
  const nodes = useMemo(() => {
    const byKey = new Map<string, SessionNode>()
    const keyOf = (id: number | null) => (id == null ? 'none' : String(id))

    for (const r of filteredRows) {
      const key = keyOf(r.sessionId)
      let node = byKey.get(key)
      if (!node) {
        node = {
          sessionId: r.sessionId,
          participantId: r.participantId,
          arm: r.arm,
          session: null,
          children: [],
        }
        byKey.set(key, node)
      }
      if (r.kind === 'SESSION') {
        node.session = r
        node.participantId = r.participantId
        node.arm = r.arm
      } else {
        node.children.push(r)
      }
    }

    const list = [...byKey.values()]
    for (const n of list) {
      n.children.sort((a, b) => rowTs(a).localeCompare(rowTs(b)) || (a.seq ?? 0) - (b.seq ?? 0))
    }
    // Newest session first (by the session's start, falling back to its first child's time).
    const nodeTs = (n: SessionNode) => n.session ? rowTs(n.session) : n.children[0] ? rowTs(n.children[0]) : ''
    list.sort((a, b) => nodeTs(b).localeCompare(nodeTs(a)))
    return list
  }, [filteredRows])

  function toggle(key: string) {
    setExpanded((prev) => {
      const next = new Set(prev)
      if (next.has(key)) next.delete(key)
      else next.add(key)
      return next
    })
  }

  function setAll(open: boolean) {
    setExpanded(open ? new Set(nodes.map((n) => (n.sessionId == null ? 'none' : String(n.sessionId)))) : new Set())
  }

  function exportCsv() {
    const header = CSV_COLUMNS.join(',')
    const lines = filteredRows.map((r) => CSV_COLUMNS.map((c) => csvCell(r[c])).join(','))
    const csv = [header, ...lines].join('\r\n')
    const url = URL.createObjectURL(new Blob([csv], { type: 'text/csv;charset=utf-8' }))
    const a = document.createElement('a')
    a.href = url
    a.download = `behavioural-log-${new Date().toISOString().slice(0, 19).replace(/[:T]/g, '-')}.csv`
    a.click()
    URL.revokeObjectURL(url)
  }

  return (
    <Container className="py-4">
      <div className="d-flex justify-content-between align-items-center mb-3 flex-wrap gap-2">
        <h1 className="h3 mb-0">Behavioural data</h1>
        <Button size="sm" onClick={exportCsv} disabled={!rows || filteredRows.length === 0}>
          Export CSV
        </Button>
      </div>

      {error && <Alert variant="danger">Couldn’t load the log: {error}</Alert>}
      {!rows && !error && <Spinner animation="border" role="status" aria-label="Loading" />}

      {rows && (
        <>
          <div className="d-flex justify-content-between align-items-center mb-2 flex-wrap gap-2">
            <Form.Control
              type="search"
              placeholder="Search all columns…"
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              style={{ maxWidth: 320 }}
            />
            <div className="d-flex align-items-center gap-2">
              <span className="text-muted small">
                {nodes.length} session{nodes.length === 1 ? '' : 's'}
              </span>
              <Button size="sm" variant="outline-secondary" onClick={() => setAll(true)} disabled={nodes.length === 0}>
                Expand all
              </Button>
              <Button size="sm" variant="outline-secondary" onClick={() => setAll(false)} disabled={nodes.length === 0}>
                Collapse all
              </Button>
            </div>
          </div>

          {nodes.length === 0 ? (
            <p className="text-center text-muted py-4 mb-0">No matching data.</p>
          ) : (
            nodes.map((n) => {
              const key = n.sessionId == null ? 'none' : String(n.sessionId)
              const isOpen = expanded.has(key)
              return (
                <div key={key} className="border rounded mb-2">
                  <div
                    role="button"
                    onClick={() => toggle(key)}
                    className="d-flex align-items-center gap-2 px-3 py-2 user-select-none"
                  >
                    <span style={{ width: 14 }}>{isOpen ? '▾' : '▸'}</span>
                    <strong>{n.participantId ?? '—'}</strong>
                    <Badge bg="secondary">{n.arm ?? '?'}</Badge>
                    <span className="text-muted small">session {n.sessionId ?? '—'}</span>
                    {n.session?.clientTs && (
                      <span className="text-muted small ms-1">{n.session.clientTs}</span>
                    )}
                    {n.session?.detail && (
                      <span className="text-muted small ms-1">· {n.session.detail}</span>
                    )}
                    <span className="text-muted small ms-auto">
                      {n.children.length} event{n.children.length === 1 ? '' : 's'}
                    </span>
                  </div>

                  {isOpen && (
                    <Table hover responsive size="sm" className="align-middle mb-0 border-top">
                      <thead>
                        <tr>
                          <th className="text-nowrap">Client time</th>
                          <th className="text-nowrap">Server time</th>
                          <th>Kind</th>
                          <th>Source</th>
                          <th>Type</th>
                          <th>Seq</th>
                          <th>Detail</th>
                        </tr>
                      </thead>
                      <tbody>
                        {n.children.map((r, i) => (
                          <tr key={i}>
                            <td className="text-nowrap small">{r.clientTs}</td>
                            <td className="text-nowrap small">{r.serverTs}</td>
                            <td className="small">{r.kind}</td>
                            <td className="small">{r.source ?? ''}</td>
                            <td className="small">{r.type}</td>
                            <td className="small">{r.seq}</td>
                            <td style={{ maxWidth: 480, whiteSpace: 'pre-wrap', wordBreak: 'break-word' }} className="small">
                              {formatDetail(r.detail)}
                            </td>
                          </tr>
                        ))}
                        {n.children.length === 0 && (
                          <tr>
                            <td colSpan={6} className="text-center text-muted py-3 small">
                              No events for this session.
                            </td>
                          </tr>
                        )}
                      </tbody>
                    </Table>
                  )}
                </div>
              )
            })
          )}
        </>
      )}
    </Container>
  )
}
