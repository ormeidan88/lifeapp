import { useState, useEffect, useRef } from 'react'
import { api } from '../api/client'
import { ConvertPicker } from '../components/ConvertPicker'

export function InboxPage() {
  const [items, setItems] = useState<any[]>([])
  const [text, setText] = useState('')
  const [loading, setLoading] = useState(true)
  const [converting, setConverting] = useState<any>(null) // the inbox item being converted
  const inputRef = useRef<HTMLInputElement>(null)

  useEffect(() => { load(); inputRef.current?.focus() }, [])
  const load = async () => { setLoading(true); const d = await api.inbox.list(); setItems(d.items); setLoading(false) }

  const add = async (e: React.FormEvent) => {
    e.preventDefault()
    if (!text.trim()) return
    await api.inbox.create(text.trim())
    setText('')
    load()
    inputRef.current?.focus()
  }

  const remove = async (id: string) => { await api.inbox.delete(id); load() }

  const handleConvert = async (target: any) => {
    if (!converting) return
    if (target.type === 'task') {
      await api.inbox.convert(converting.id, { targetType: 'task', projectId: target.projectId })
    } else if (target.type === 'project') {
      await api.inbox.convert(converting.id, { targetType: 'project', name: target.name })
    } else if (target.type === 'list-item') {
      await api.inbox.convert(converting.id, { targetType: 'list-item', listId: target.listId, projectId: target.projectId })
    }
    setConverting(null)
    load()
  }

  const timeAgo = (iso: string) => {
    const diff = Date.now() - new Date(iso).getTime()
    const mins = Math.floor(diff / 60000)
    if (mins < 1) return 'just now'
    if (mins < 60) return `${mins}m ago`
    const hrs = Math.floor(mins / 60)
    if (hrs < 24) return `${hrs}h ago`
    return `${Math.floor(hrs / 24)}d ago`
  }

  return (
    <div className="max-w-3xl mx-auto">
      <div className="flex items-center justify-between mb-6">
        <h1 className="text-2xl font-semibold">Inbox</h1>
        {items.length > 0 && <span className="text-sm text-[var(--text-muted)]">{items.length} items</span>}
      </div>
      <form onSubmit={add} className="mb-6">
        <input ref={inputRef} value={text} onChange={e => setText(e.target.value)}
          placeholder="What's on your mind?" autoFocus
          className="w-full px-4 py-3.5 border border-[var(--border)] rounded-xl outline-none text-base"
          style={{ background: 'var(--bg-card)', boxShadow: 'var(--shadow-xs)' }} />
      </form>
      {loading && items.length === 0 && <p className="text-[var(--text-muted)] text-center py-8">Loading...</p>}
      <div className="space-y-2">
        {items.map(item => (
          <div
            key={item.id}
            className="flex items-center px-4 py-3 rounded-xl border border-[var(--border)] group transition-all"
            style={{ background: 'var(--bg-card)', boxShadow: 'var(--shadow-xs)' }}
            onMouseEnter={e => { (e.currentTarget as HTMLElement).style.boxShadow = 'var(--shadow-sm)' }}
            onMouseLeave={e => { (e.currentTarget as HTMLElement).style.boxShadow = 'var(--shadow-xs)' }}
          >
            <div className="flex-1 min-w-0">
              <p className="text-[0.95rem]">{item.text}</p>
              <p className="text-[10px] text-[var(--text-muted)] mt-0.5">{timeAgo(item.createdAt)}</p>
            </div>
            <div className="flex gap-1 ml-3 opacity-0 group-hover:opacity-100 transition-opacity flex-shrink-0">
              <button onClick={() => setConverting(item)} title="Move to project or list"
                className="text-[10px] px-2 py-1 rounded-md bg-[var(--accent-sage-light)] text-[var(--accent-sage)] hover:bg-[var(--accent-sage)] hover:text-white transition-colors">
                Move →
              </button>
              <button onClick={() => remove(item.id)} title="Delete"
                className="w-7 h-7 rounded-md flex items-center justify-center text-[var(--text-muted)] hover:bg-[var(--danger-light)] hover:text-[var(--danger)] transition-colors text-xs">✕</button>
            </div>
          </div>
        ))}
        {!loading && items.length === 0 && (
          <div className="text-center py-16">
            <div className="text-4xl mb-3">📥</div>
            <p className="text-[var(--text-muted)]">Your inbox is empty</p>
            <p className="text-xs text-[var(--text-muted)] mt-1">Type above to capture a thought</p>
          </div>
        )}
      </div>

      {/* Convert picker modal */}
      {converting && (
        <ConvertPicker
          itemText={converting.text}
          onConvert={handleConvert}
          onClose={() => setConverting(null)}
        />
      )}
    </div>
  )
}
