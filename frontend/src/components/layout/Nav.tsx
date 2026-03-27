import React from 'react'

// ── SVG icon set — 16×16, 1.4px stroke, consistent visual weight ──────────
function NavIcon({ name }: { name: string }) {
  const icons: Record<string, React.ReactNode> = {
    inbox: (
      <svg viewBox="0 0 16 16" fill="none" stroke="currentColor" strokeWidth="1.4" strokeLinecap="round" strokeLinejoin="round">
        <path d="M2 10h12M2 10v3a.5.5 0 00.5.5h11a.5.5 0 00.5-.5v-3M2 10l1.5-6h9L14 10" />
        <path d="M6 7h4" />
      </svg>
    ),
    projects: (
      <svg viewBox="0 0 16 16" fill="none" stroke="currentColor" strokeWidth="1.4" strokeLinecap="round" strokeLinejoin="round">
        <rect x="1.5" y="1.5" width="5.5" height="5.5" rx="1.2" />
        <rect x="9" y="1.5" width="5.5" height="5.5" rx="1.2" />
        <rect x="1.5" y="9" width="5.5" height="5.5" rx="1.2" />
        <rect x="9" y="9" width="5.5" height="5.5" rx="1.2" />
      </svg>
    ),
    lists: (
      <svg viewBox="0 0 16 16" fill="none" stroke="currentColor" strokeWidth="1.4" strokeLinecap="round" strokeLinejoin="round">
        <circle cx="2.5" cy="4.5" r=".6" fill="currentColor" stroke="none" />
        <path d="M5.5 4.5h8" />
        <circle cx="2.5" cy="8" r=".6" fill="currentColor" stroke="none" />
        <path d="M5.5 8h8" />
        <circle cx="2.5" cy="11.5" r=".6" fill="currentColor" stroke="none" />
        <path d="M5.5 11.5h8" />
      </svg>
    ),
    pages: (
      <svg viewBox="0 0 16 16" fill="none" stroke="currentColor" strokeWidth="1.4" strokeLinecap="round" strokeLinejoin="round">
        <path d="M3.5 2h7L13 4.5V13.5a.5.5 0 01-.5.5h-9a.5.5 0 01-.5-.5V2.5a.5.5 0 01.5-.5z" />
        <path d="M10.5 2v2.5H13" />
        <path d="M5.5 7h5M5.5 9.5h3.5" />
      </svg>
    ),
    habits: (
      <svg viewBox="0 0 16 16" fill="none" stroke="currentColor" strokeWidth="1.4" strokeLinecap="round" strokeLinejoin="round">
        <circle cx="8" cy="8" r="5.5" />
        <path d="M5.5 8l2 2 3.5-3.5" />
      </svg>
    ),
    memorize: (
      <svg viewBox="0 0 16 16" fill="none" stroke="currentColor" strokeWidth="1.4" strokeLinecap="round" strokeLinejoin="round">
        <rect x="1.5" y="4.5" width="9.5" height="7" rx="1.2" />
        <path d="M5 4.5V3a1 1 0 011-1h7.5a1 1 0 011 1v7a1 1 0 01-1 1H11" />
      </svg>
    ),
    books: (
      <svg viewBox="0 0 16 16" fill="none" stroke="currentColor" strokeWidth="1.4" strokeLinecap="round" strokeLinejoin="round">
        <path d="M8 13V4.5M8 4.5C8 4.5 6.5 3 4 3.5V13c2.5-.5 4 1 4 1M8 4.5C8 4.5 9.5 3 12 3.5V13c-2.5-.5-4 1-4 1" />
      </svg>
    ),
    calendar: (
      <svg viewBox="0 0 16 16" fill="none" stroke="currentColor" strokeWidth="1.4" strokeLinecap="round" strokeLinejoin="round">
        <rect x="1.5" y="3" width="13" height="11" rx="1.5" />
        <path d="M1.5 7h13M5 1.5v3M11 1.5v3" />
        <rect x="3.5" y="9" width="2" height="2" rx=".5" fill="currentColor" stroke="none" />
        <rect x="9" y="9" width="2" height="2" rx=".5" fill="currentColor" stroke="none" />
      </svg>
    ),
  }
  return (
    <span className="w-4 h-4 flex-shrink-0 flex items-center justify-center">
      {icons[name]}
    </span>
  )
}

// ── Label display names ──────────────────────────────────────────────────
const labels: Record<string, string> = {
  inbox: 'Inbox',
  projects: 'Projects',
  lists: 'Lists',
  pages: 'Pages',
  habits: 'Habits',
  memorize: 'Memorize',
  books: 'Books',
  calendar: 'Calendar',
}

const items = ['inbox', 'projects', 'lists', 'pages', 'habits', 'memorize', 'books', 'calendar']

type Props = { current: string; onNavigate: (page: string) => void; onLogout: () => void }

// ── Sidebar (desktop) ────────────────────────────────────────────────────
export function Sidebar({ current, onNavigate, onLogout }: Props) {
  return (
    <nav className="sidebar-nav hidden md:flex flex-col w-48 h-screen fixed left-0 top-0 justify-between py-5">
      {/* Logo */}
      <div className="flex flex-col gap-0.5 px-3">
        <div className="flex items-center gap-2.5 px-2.5 mb-6">
          <div
            className="w-7 h-7 rounded-lg flex items-center justify-center text-white text-xs font-bold flex-shrink-0"
            style={{ background: 'var(--accent-sage)', fontFamily: "'Lora', Georgia, serif", fontStyle: 'italic' }}
          >
            L
          </div>
          <span className="font-medium text-sm tracking-tight" style={{ color: 'var(--text)' }}>LifeApp</span>
        </div>

        {/* Nav items */}
        {items.map(item => {
          const active = current === item
          return (
            <button
              key={item}
              onClick={() => onNavigate(item)}
              className="flex items-center gap-2.5 px-2.5 py-2 rounded-lg text-sm transition-all w-full text-left"
              style={{
                color: active ? 'var(--text)' : 'var(--text-muted)',
                background: active ? 'rgba(126, 155, 106, 0.13)' : 'transparent',
                fontWeight: active ? '500' : '400',
              }}
              onMouseEnter={e => {
                if (!active) (e.currentTarget as HTMLElement).style.background = 'rgba(44, 35, 20, 0.05)'
              }}
              onMouseLeave={e => {
                if (!active) (e.currentTarget as HTMLElement).style.background = 'transparent'
              }}
            >
              <NavIcon name={item} />
              <span>{labels[item]}</span>
            </button>
          )
        })}
      </div>

      {/* Logout */}
      <div className="px-3">
        <div className="h-px mx-2 mb-3" style={{ background: 'var(--border-subtle)' }} />
        <button
          onClick={onLogout}
          className="flex items-center gap-2.5 px-2.5 py-2 rounded-lg text-sm w-full text-left transition-all"
          style={{ color: 'var(--text-muted)' }}
          onMouseEnter={e => {
            ;(e.currentTarget as HTMLElement).style.color = 'var(--danger)'
            ;(e.currentTarget as HTMLElement).style.background = 'var(--danger-light)'
          }}
          onMouseLeave={e => {
            ;(e.currentTarget as HTMLElement).style.color = 'var(--text-muted)'
            ;(e.currentTarget as HTMLElement).style.background = 'transparent'
          }}
        >
          <svg className="w-4 h-4 flex-shrink-0" viewBox="0 0 16 16" fill="none" stroke="currentColor" strokeWidth="1.4" strokeLinecap="round" strokeLinejoin="round">
            <path d="M10 8H2M2 8l2.5-2.5M2 8l2.5 2.5" />
            <path d="M7 5V3a.5.5 0 01.5-.5h6a.5.5 0 01.5.5v10a.5.5 0 01-.5.5h-6A.5.5 0 017 13v-2" />
          </svg>
          <span>Logout</span>
        </button>
      </div>
    </nav>
  )
}

// ── Bottom nav (mobile) ──────────────────────────────────────────────────
export function BottomNav({ current, onNavigate }: Omit<Props, 'onLogout'>) {
  return (
    <nav
      className="bottom-nav-glass md:hidden fixed bottom-0 left-0 right-0 flex justify-around py-2 z-50"
      style={{ paddingBottom: 'max(0.5rem, env(safe-area-inset-bottom))' }}
    >
      {items.map(item => {
        const active = current === item
        return (
          <button
            key={item}
            onClick={() => onNavigate(item)}
            className="flex flex-col items-center gap-0.5 px-2 py-1 rounded-lg transition-all min-w-[44px]"
            style={{ color: active ? 'var(--accent-sage)' : 'var(--text-muted)' }}
          >
            <span className="w-5 h-5 flex items-center justify-center">
              <NavIcon name={item} />
            </span>
            <span className="text-[9.5px] leading-tight font-medium">{labels[item]}</span>
          </button>
        )
      })}
    </nav>
  )
}
