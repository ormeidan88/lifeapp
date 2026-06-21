import { useState, useEffect, useRef } from 'react'
import { api } from '../../api/client'

type Result = { id: string; title: string; type: 'page' | 'thought' }

type Props = {
  onSelect: (pageId: string, title: string, type: 'page' | 'thought') => void
  onClose: () => void
}

export function PageLinkPicker({ onSelect, onClose }: Props) {
  const [query, setQuery] = useState('')
  const [results, setResults] = useState<Result[]>([])
  const [selected, setSelected] = useState(0)
  const inputRef = useRef<HTMLInputElement>(null)

  useEffect(() => { inputRef.current?.focus() }, [])

  useEffect(() => {
    if (query.trim().length === 0) { setResults([]); return }
    const t = setTimeout(async () => {
      const [p, th] = await Promise.all([api.pages.search(query), api.thoughts.search(query)])
      const merged: Result[] = [
        ...(p.pages || []).map((r: any) => ({ id: r.id, title: r.title, type: 'page' as const })),
        ...(th.thoughts || []).map((r: any) => ({ id: r.id, title: r.title, type: 'thought' as const })),
      ]
      setResults(merged)
    }, 200)
    return () => clearTimeout(t)
  }, [query])

  const createAndSelect = async () => {
    if (!query.trim()) return
    const page = await api.pages.create({ title: query.trim(), ownerType: 'standalone' })
    onSelect(page.id, query.trim(), 'page')
  }

  const handleKey = (e: React.KeyboardEvent) => {
    const total = results.length + 1 // +1 for "Create new" option
    if (e.key === 'ArrowDown') { e.preventDefault(); setSelected(s => (s + 1) % total) }
    else if (e.key === 'ArrowUp') { e.preventDefault(); setSelected(s => (s - 1 + total) % total) }
    else if (e.key === 'Enter') {
      e.preventDefault()
      if (selected < results.length) onSelect(results[selected].id, results[selected].title, results[selected].type)
      else createAndSelect()
    }
    else if (e.key === 'Escape') onClose()
  }

  return (
    <div className="absolute z-50 bg-[var(--bg-card)] border border-[var(--border)] rounded-lg w-64 overflow-hidden" style={{ boxShadow: 'var(--shadow-float)' }}>
      <input ref={inputRef} value={query} onChange={e => { setQuery(e.target.value); setSelected(0) }}
        onKeyDown={handleKey} placeholder="Search or create page..."
        className="w-full px-3 py-2 border-b border-[var(--border)] outline-none text-sm" />
      <div className="max-h-48 overflow-y-auto">
        {results.map((r, i) => (
          <button key={`${r.type}-${r.id}`} onClick={() => onSelect(r.id, r.title, r.type)}
            className={`w-full text-left px-3 py-1.5 text-sm flex items-center gap-2 ${i === selected ? 'bg-[var(--bg-surface)]' : 'hover:bg-[var(--bg)]'}`}>
            <span>{r.type === 'thought' ? '💭' : '📄'}</span> {r.title}
          </button>
        ))}
        {query.trim() && (
          <button onClick={createAndSelect}
            className={`w-full text-left px-3 py-1.5 text-sm flex items-center gap-2 ${selected === results.length ? 'bg-[var(--bg-surface)]' : 'hover:bg-[var(--bg)]'}`}>
            <span>➕</span> Create "{query.trim()}"
          </button>
        )}
        {!query.trim() && <p className="px-3 py-2 text-xs text-[var(--text-muted)]">Type a page name...</p>}
      </div>
    </div>
  )
}
