import { useState, useEffect } from 'react'
import { api } from '../api/client'
import { PageEditor } from '../components/editor/PageEditor'

type Props = {
  externalPageId?: string | null
  onClearExternal?: () => void
}

export function PagesPage({ externalPageId, onClearExternal }: Props) {
  const [pages, setPages] = useState<any[]>([])
  const [selected, setSelected] = useState<any>(null)
  const [childPages, setChildPages] = useState<any[]>([])
  const [adding, setAdding] = useState(false)
  const [title, setTitle] = useState('')
  const [editing, setEditing] = useState(false)
  const [editTitle, setEditTitle] = useState('')
  const [loading, setLoading] = useState(true)

  const [history, setHistory] = useState<string[]>([]) // page ID stack for back navigation

  useEffect(() => { load() }, [])

  useEffect(() => {
    if (externalPageId) openPage(externalPageId, false)
  }, [externalPageId])

  const load = async () => { setLoading(true); const d = await api.pages.list({ ownerType: 'standalone' }); setPages(d.pages); setLoading(false) }

  const add = async (e: React.FormEvent) => {
    e.preventDefault()
    if (!title.trim()) return
    await api.pages.create({ title: title.trim(), ownerType: 'standalone' })
    setTitle(''); setAdding(false); load()
  }

  const openPage = async (id: string, pushHistory = true) => {
    if (pushHistory && selected) setHistory(h => [...h, selected.id])
    const p = await api.pages.get(id)
    setSelected(p)
    setEditTitle(p.title)
    const children = await api.pages.list({ parentPageId: id })
    setChildPages(children.pages || [])
  }

  const save = async (content: any) => { if (selected) await api.pages.update(selected.id, { content }) }

  const saveTitle = async () => {
    if (!selected || !editTitle.trim()) return
    await api.pages.update(selected.id, { title: editTitle.trim() })
    setSelected({ ...selected, title: editTitle.trim() })
    setEditing(false)
  }

  const addChildPage = async () => {
    const name = prompt('Child page title:')
    if (!name?.trim() || !selected) return
    await api.pages.create({ title: name.trim(), parentPageId: selected.id, ownerType: 'standalone' })
    const children = await api.pages.list({ parentPageId: selected.id })
    setChildPages(children.pages || [])
  }

  const deletePage = async () => {
    if (!selected || !confirm('Delete this page and all child pages?')) return
    await api.pages.delete(selected.id)
    back()
  }

  const back = () => {
    if (history.length > 0) {
      const prev = history[history.length - 1]
      setHistory(h => h.slice(0, -1))
      openPage(prev, false)
    } else {
      setSelected(null); setChildPages([]); setHistory([])
      if (onClearExternal) onClearExternal()
      load()
    }
  }

  if (selected) {
    return (
      <div>
        <div className="flex items-center justify-between mb-4">
          <button onClick={back} className="text-[var(--text-muted)] hover:text-[var(--text)]">← Back</button>
          <div className="flex gap-3">
            <button onClick={addChildPage} className="text-xs text-[var(--accent-sage)] hover:underline">+ Child page</button>
            <button onClick={deletePage} className="text-xs text-[var(--danger)] hover:underline">Delete</button>
          </div>
        </div>
        {editing ? (
          <form onSubmit={e => { e.preventDefault(); saveTitle() }} className="flex gap-2 mb-4">
            <input value={editTitle} onChange={e => setEditTitle(e.target.value)} autoFocus
              onKeyDown={e => e.key === 'Escape' && setEditing(false)}
              className="text-2xl font-semibold flex-1 px-2 py-1 border border-[var(--border)] rounded outline-none focus:border-[var(--accent-sage)]" />
            <button type="submit" className="text-sm text-[var(--accent-sage)]">Save</button>
          </form>
        ) : (
          <h1 className="text-2xl font-semibold mb-4 cursor-pointer hover:text-[var(--accent-sage)]"
            onClick={() => setEditing(true)} title="Click to rename">
            {selected.title}
          </h1>
        )}
        {/* Child pages */}
        {childPages.length > 0 && (
          <div className="mb-4 space-y-1">
            {childPages.map(cp => (
              <button key={cp.id} onClick={() => openPage(cp.id)}
                className="flex items-center gap-2 text-sm text-[var(--accent-blue)] hover:underline">
                📄 {cp.title}
              </button>
            ))}
          </div>
        )}
        <PageEditor content={selected.content} onUpdate={save} />
      </div>
    )
  }

  return (
    <div>
      <div className="flex items-center justify-between mb-6">
        <h1 className="text-2xl font-semibold">Pages</h1>
        <button onClick={() => setAdding(true)} className="px-3 py-1.5 bg-[var(--accent-sage)] text-white rounded-lg text-sm hover:opacity-90">+ New</button>
      </div>
      {adding && (
        <form onSubmit={add} className="mb-4 flex gap-2">
          <input value={title} onChange={e => setTitle(e.target.value)} placeholder="Page title..." autoFocus
            onKeyDown={e => e.key === 'Escape' && (setAdding(false), setTitle(''))}
            className="flex-1 px-3 py-2 border border-[var(--border)] rounded-lg outline-none focus:border-[var(--accent-sage)]" />
          <button type="submit" className="px-3 py-2 bg-[var(--accent-sage)] text-white rounded-lg text-sm">Create</button>
          <button type="button" onClick={() => { setAdding(false); setTitle('') }} className="px-3 py-2 text-sm text-[var(--text-muted)]">Cancel</button>
        </form>
      )}
      {loading && pages.length === 0 && <p className="text-[var(--text-muted)] text-center py-12">Loading...</p>}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
        {pages.map(p => (
          <div key={p.id} onClick={() => openPage(p.id)}
            className="bg-white p-4 rounded-xl border border-[var(--border)] cursor-pointer hover:shadow-sm transition-shadow">
            <h3 className="font-medium">📝 {p.title}</h3>
          </div>
        ))}
      </div>
      {!loading && pages.length === 0 && !adding && <p className="text-[var(--text-muted)] text-center py-12">No pages yet</p>}
    </div>
  )
}
