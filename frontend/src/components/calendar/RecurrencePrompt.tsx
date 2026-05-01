type Props = {
  onChoice: (mode: 'single' | 'all') => void
  onCancel: () => void
  action: 'edit' | 'delete' | 'move'
}

export function RecurrencePrompt({ onChoice, onCancel, action }: Props) {
  const labels = { edit: 'Edit recurring event', delete: 'Delete recurring event', move: 'Move recurring event' }
  return (
    <div className="fixed inset-0 z-[60] flex items-center justify-center" onClick={onCancel}>
      <div className="absolute inset-0 bg-black/20" />
      <div className="relative bg-[var(--bg-card)] rounded-2xl border border-[var(--border)] w-72"
        style={{ boxShadow: 'var(--shadow-float)' }} onClick={e => e.stopPropagation()}>
        <div className="p-4">
          <p className="text-sm font-medium mb-3">{labels[action]}</p>
          <div className="space-y-1.5">
            <button onClick={() => onChoice('single')}
              className="w-full text-left px-3 py-2 rounded-lg text-sm hover:bg-[var(--bg-surface)] transition-colors text-[var(--text)]">
              This event only
            </button>
            <button onClick={() => onChoice('all')}
              className="w-full text-left px-3 py-2 rounded-lg text-sm hover:bg-[var(--bg-surface)] transition-colors text-[var(--text)]">
              All events in series
            </button>
          </div>
        </div>
        <div className="px-4 pb-3">
          <button onClick={onCancel} className="w-full text-center text-xs text-[var(--text-muted)] hover:text-[var(--text)] transition-colors">Cancel</button>
        </div>
      </div>
    </div>
  )
}
