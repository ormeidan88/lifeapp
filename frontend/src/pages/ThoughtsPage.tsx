import { useState, useEffect } from 'react'
import { api } from '../api/client'
import { PageEditor } from '../components/editor/PageEditor'

type Props = {
  externalThoughtId?: string | null
  onClearExternal?: () => void
}

export function ThoughtsPage({ externalThoughtId, onClearExternal }: Props) {
  const [daily, setDaily] = useState<any[]>([])
  const [subjects, setSubjects] = useState<any[]>([])
  const [selected, setSelected] = useState<any>(null)
  const [childThoughts, setChildThoughts] = useState<any[]>([])
  const [addingSubject, setAddingSubject] = useState(false)
  const [title, setTitle] = useState('')
  const [editing, setEditing] = useState(false)
  const [editTitle, setEditTitle] = useState('')
  const [loading, setLoading] = useState(true)

  const [history, setHistory] = useState<string[]>([]) // thought ID stack for back navigation

  useEffect(() => { load() }, [])

  useEffect(() => {
    if (externalThoughtId) openThought(externalThoughtId, false)
  }, [externalThoughtId])

  const load = async () => {
    setLoading(true)
    const [d, s] = await Promise.all([
      api.thoughts.list({ kind: 'daily' }),
      api.thoughts.list({ kind: 'subject' }),
    ])
    setDaily(d.thoughts || [])
    setSubjects(s.thoughts || [])
    setLoading(false)
  }

  const newDaily = async () => {
    const t = await api.thoughts.createDaily()
    openThought(t.id)
  }

  const addSubject = async (e: React.FormEvent) => {
    e.preventDefault()
    if (!title.trim()) return
    const t = await api.thoughts.create({ kind: 'subject', title: title.trim() })
    setTitle(''); setAddingSubject(false)
    openThought(t.id)
  }

  const openThought = async (id: string, pushHistory = true) => {
    if (pushHistory && selected) setHistory(h => [...h, selected.id])
    const t = await api.thoughts.get(id)
    setSelected(t)
    setEditTitle(t.title)
    const children = await api.thoughts.list({ parentThoughtId: id })
    setChildThoughts(children.thoughts || [])
  }

  const save = async (content: any) => { if (selected) await api.thoughts.update(selected.id, { content }) }

  const saveTitle = async () => {
    if (!selected || !editTitle.trim()) return
    await api.thoughts.update(selected.id, { title: editTitle.trim() })
    setSelected({ ...selected, title: editTitle.trim() })
    setEditing(false)
  }

  const addChildThought = async () => {
    const name = prompt('Child thought title:')
    if (!name?.trim() || !selected) return
    await api.thoughts.create({ kind: 'subject', title: name.trim(), parentThoughtId: selected.id })
    const children = await api.thoughts.list({ parentThoughtId: selected.id })
    setChildThoughts(children.thoughts || [])
  }

  const deleteThought = async () => {
    if (!selected || !confirm('Delete this thought and all child thoughts?')) return
    await api.thoughts.delete(selected.id)
    back()
  }

  const back = () => {
    if (history.length > 0) {
      const prev = history[history.length - 1]
      setHistory(h => h.slice(0, -1))
      openThought(prev, false)
    } else {
      setSelected(null); setChildThoughts([]); setHistory([])
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
            <button onClick={addChildThought} className="text-xs text-[var(--accent-sage)] hover:underline">+ Child thought</button>
            <button onClick={deleteThought} className="text-xs text-[var(--danger)] hover:underline">Delete</button>
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
        {/* Child thoughts */}
        {childThoughts.length > 0 && (
          <div className="mb-4 space-y-1">
            {childThoughts.map(ct => (
              <button key={ct.id} onClick={() => openThought(ct.id)}
                className="flex items-center gap-2 text-sm text-[var(--accent-blue)] hover:underline">
                💭 {ct.title}
              </button>
            ))}
          </div>
        )}
        <PageEditor content={selected.content} onUpdate={save} />
      </div>
    )
  }

  const card = (t: any) => (
    <div key={t.id} onClick={() => openThought(t.id)}
      className="bg-[var(--bg-card)] p-4 rounded-xl border border-[var(--border)] cursor-pointer transition-[box-shadow] duration-150"
      style={{ boxShadow: 'var(--shadow-card)' }}
      onMouseEnter={e => { (e.currentTarget as HTMLElement).style.boxShadow = 'var(--shadow-card-hover)' }}
      onMouseLeave={e => { (e.currentTarget as HTMLElement).style.boxShadow = 'var(--shadow-card)' }}>
      <h3 className="font-medium">{t.title}</h3>
    </div>
  )

  return (
    <div>
      <div className="flex items-center justify-between mb-6">
        <h1 className="text-2xl font-semibold">Thoughts</h1>
        <div className="flex gap-2">
          <button onClick={newDaily} className="px-3 py-1.5 bg-[var(--accent-sage)] text-white rounded-lg text-sm hover:opacity-90">+ New Daily Thought</button>
          <button onClick={() => setAddingSubject(true)} className="px-3 py-1.5 bg-[var(--accent-sage)] text-white rounded-lg text-sm hover:opacity-90">+ New Subject</button>
        </div>
      </div>
      {addingSubject && (
        <form onSubmit={addSubject} className="mb-4 flex gap-2">
          <input value={title} onChange={e => setTitle(e.target.value)} placeholder="Subject title..." autoFocus
            onKeyDown={e => e.key === 'Escape' && (setAddingSubject(false), setTitle(''))}
            className="flex-1 px-3 py-2 border border-[var(--border)] rounded-lg outline-none focus:border-[var(--accent-sage)]" />
          <button type="submit" className="px-3 py-2 bg-[var(--accent-sage)] text-white rounded-lg text-sm">Create</button>
          <button type="button" onClick={() => { setAddingSubject(false); setTitle('') }} className="px-3 py-2 text-sm text-[var(--text-muted)]">Cancel</button>
        </form>
      )}
      {loading && daily.length === 0 && subjects.length === 0 && <p className="text-[var(--text-muted)] text-center py-12">Loading...</p>}

      {/* Daily Thoughts */}
      <h2 className="text-lg font-semibold mb-3">Daily Thoughts</h2>
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
        {daily.map(card)}
      </div>
      {!loading && daily.length === 0 && <p className="text-[var(--text-muted)] text-sm py-4">No daily thoughts yet</p>}

      {/* Subjects */}
      <h2 className="text-lg font-semibold mb-3 mt-8">Subjects</h2>
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
        {subjects.map(card)}
      </div>
      {!loading && subjects.length === 0 && <p className="text-[var(--text-muted)] text-sm py-4">No subjects yet</p>}
    </div>
  )
}
