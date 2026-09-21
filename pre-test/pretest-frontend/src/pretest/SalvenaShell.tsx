import type { ReactNode } from 'react'

/**
 * Chrome for the study's own screens: a single Salvena header over a centred column.
 *
 * <p>Not the portal's layout, on purpose. The outcome screen is where Salvena delivers its
 * decision, and stripping the navigation away puts the decision on its own and gives the
 * participant nothing to click past it with. The portal's full layout is what they used to get
 * here, and it is what they return to if the arm sends them back to try again.
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
        </div>
      </header>
      <main style={{ maxWidth: 760, margin: '0 auto', padding: '24px 20px 56px' }}>{children}</main>
    </div>
  )
}
