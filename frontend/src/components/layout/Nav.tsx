const icons: Record<string, string> = {
  inbox: '📥', projects: '📋', lists: '📌', pages: '📝', habits: '✅',
  memorize: '🧠', books: '📚', calendar: '📅',
}

type Props = { current: string; onNavigate: (page: string) => void; onLogout: () => void }

const items = ['inbox', 'projects', 'lists', 'pages', 'habits', 'memorize', 'books', 'calendar']

export function Sidebar({ current, onNavigate, onLogout }: Props) {
  return (
    <nav className="hidden md:flex flex-col w-48 bg-white border-r border-[var(--border)] h-screen fixed left-0 top-0 justify-between py-4">
      <div className="flex flex-col gap-0.5 px-3">
        {/* Logo */}
        <div className="flex items-center gap-2.5 px-2 mb-5">
          <div className="w-8 h-8 rounded-lg bg-[var(--accent-sage)] flex items-center justify-center text-white text-sm font-bold">
            L
          </div>
          <span className="font-semibold text-sm">LifeApp</span>
        </div>
        {items.map(item => (
          <button key={item} onClick={() => onNavigate(item)}
            className={`flex items-center gap-2.5 px-2.5 py-2 rounded-lg text-sm transition-all w-full text-left
              ${current === item
                ? 'bg-[var(--accent-sage-light)] text-[var(--text)] font-medium'
                : 'text-[var(--text-muted)] hover:bg-[var(--bg)] hover:text-[var(--text)]'}`}>
            <span className="text-base leading-none">{icons[item]}</span>
            <span className="capitalize">{item}</span>
          </button>
        ))}
      </div>
      <div className="px-3">
        <button onClick={onLogout}
          className="flex items-center gap-2.5 px-2.5 py-2 rounded-lg text-sm text-[var(--text-muted)] hover:bg-[var(--bg)] hover:text-[var(--danger)] transition-colors w-full text-left">
          <span className="text-base leading-none">⏻</span>
          <span>Logout</span>
        </button>
      </div>
    </nav>
  )
}

export function BottomNav({ current, onNavigate }: Omit<Props, 'onLogout'>) {
  return (
    <nav className="md:hidden fixed bottom-0 left-0 right-0 bg-white border-t border-[var(--border)] flex justify-around py-1.5 z-50"
      style={{ paddingBottom: 'max(0.375rem, env(safe-area-inset-bottom))' }}>
      {items.map(item => (
        <button key={item} onClick={() => onNavigate(item)}
          className={`flex flex-col items-center text-[10px] gap-0.5 px-1 py-0.5 rounded-lg transition-colors
            ${current === item ? 'text-[var(--accent-sage)]' : 'text-[var(--text-muted)]'}`}>
          <span className="text-lg leading-none">{icons[item]}</span>
          <span className="capitalize">{item}</span>
        </button>
      ))}
    </nav>
  )
}
