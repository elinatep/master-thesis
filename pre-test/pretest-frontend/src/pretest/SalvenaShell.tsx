import type { ReactNode } from 'react'
import AdminMenu from '../study/AdminMenu'

/**
 * The Salvena portal chrome: a single header bar over a centred column.
 *
 * <p>Deliberately not the full portal layout. The pre-test's mockups show one header and one
 * panel - no dashboard, no policy list, no claims table - because the pre-test is not asking
 * whether people can navigate a portal. It is asking whether a claim outcome lands emotionally.
 * Extra surface to explore would be extra variance between participants and nothing else.
 */
export default function SalvenaShell({ children }: { children: ReactNode }) {
  return (
    <div style={{ minHeight: '100vh', background: 'var(--bs-body-bg, #f6f7f9)' }}>
      <header
        style={{
          background: '#fff',
          borderBottom: '1px solid rgba(0,0,0,.1)',
          padding: '14px 20px',
        }}
      >
        <div
          style={{
            maxWidth: 760,
            margin: '0 auto',
            fontWeight: 600,
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
            gap: 12,
          }}
        >
          <span>
            Salvena <span className="text-muted fw-normal">|</span>{' '}
            <span className="fw-normal">Customer Portal</span>
          </span>
          {/* Hidden unless a researcher types showAdmin() in the console - see adminVisibility. */}
          <AdminMenu />
        </div>
      </header>
      <main style={{ maxWidth: 760, margin: '0 auto', padding: '24px 20px 56px' }}>{children}</main>
    </div>
  )
}
