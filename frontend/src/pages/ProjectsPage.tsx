import { useState, useEffect } from 'react'
import { api } from '../api/client'

const statusConfig: Record<string, { bg: string; text: string; border: string; label: string }> = {
  NOT_STARTED: { bg: 'bg-gray-50', text: 'text-gray-500', border: 'border-l-gray-300', label: 'Not Started' },
  IN_PROGRESS: { bg: 'bg-[var(--accent-sage-light)]', text: 'text-[var(--accent-sage)]', border: 'border-l-[var(--accent-sage)]', label: 'In Progress' },
  DONE: { bg: 'bg-green-50', text: 'text-green-700', border: 'border-l-green-500', label: 'Done' },
  CANCELLED: { bg: 'bg-[var(--danger-light)]', text: 'text-[var(--danger)]', border: 'border-l-[var(--danger)]', label: 'Cancelled' },
}

export function ProjectsPage({ onOpen }: { onOpen: (id: string) => void }) {
  const [projects, setProjects] = useState<any[]>([])
  const [name, setName] = useState('')
  const [adding, setAdding] = useState(false)
  const [loading, setLoading] = useState(true)

  useEffect(() => { load() }, [])
  const load = async () => { setLoading(true); const d = await api.projects.list(); setProjects(d.projects); setLoading(false) }

  const add = async (e: React.FormEvent) => {
    e.preventDefault()
    if (!name.trim()) return
    await api.projects.create(name.trim())
    setName(''); setAdding(false); load()
  }
  const cancel = () => { setAdding(false); setName('') }

  return (
    <div>
      <div className="flex items-center justify-between mb-6">
        <div>
          <h1 className="text-2xl font-semibold">Projects</h1>
          {projects.length > 0 && <p className="text-xs text-[var(--text-muted)] mt-0.5">{projects.filter(p => p.status === 'IN_PROGRESS').length} active · {projects.length} total</p>}
        </div>
        <button onClick={() => setAdding(true)} className="px-3 py-1.5 bg-[var(--accent-sage)] text-white rounded-lg text-sm font-medium hover:opacity-90 active:scale-95 transition-all">+ New</button>
      </div>
      {adding && (
        <form onSubmit={add} className="mb-5 flex gap-2">
          <input value={name} onChange={e => setName(e.target.value)} placeholder="Project name..." autoFocus
            onKeyDown={e => e.key === 'Escape' && cancel()}
            className="flex-1 px-3 py-2 border border-[var(--border)] rounded-lg outline-none text-sm" />
          <button type="submit" className="px-3 py-2 bg-[var(--accent-sage)] text-white rounded-lg text-sm">Create</button>
          <button type="button" onClick={cancel} className="px-3 py-2 text-sm text-[var(--text-muted)]">Cancel</button>
        </form>
      )}
      {loading && projects.length === 0 && <p className="text-[var(--text-muted)] text-center py-12">Loading...</p>}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-3">
        {projects.map(p => {
          const s = statusConfig[p.status] || statusConfig.NOT_STARTED
          return (
            <div
              key={p.id}
              onClick={() => onOpen(p.id)}
              className={`p-4 rounded-xl border border-[var(--border)] border-l-4 ${s.border} cursor-pointer transition-all active:scale-[0.98]`}
              style={{
                background: 'var(--bg-card)',
                boxShadow: 'var(--shadow-card)',
              }}
              onMouseEnter={e => {
                ;(e.currentTarget as HTMLElement).style.boxShadow = 'var(--shadow-card-hover)'
                ;(e.currentTarget as HTMLElement).style.transform = 'translateY(-1px)'
              }}
              onMouseLeave={e => {
                ;(e.currentTarget as HTMLElement).style.boxShadow = 'var(--shadow-card)'
                ;(e.currentTarget as HTMLElement).style.transform = 'translateY(0)'
              }}
            >
              <h3 className="font-medium text-[0.925rem] mb-2.5 leading-snug">{p.name}</h3>
              <span className={`text-[10px] font-medium px-2 py-0.5 rounded-full ${s.bg} ${s.text}`}>{s.label}</span>
            </div>
          )
        })}
      </div>
      {!loading && projects.length === 0 && !adding && (
        <div className="text-center py-16">
          <div className="text-4xl mb-3">📋</div>
          <p className="text-[var(--text-muted)]">No projects yet</p>
          <p className="text-xs text-[var(--text-muted)] mt-1">Create one to get started</p>
        </div>
      )}
    </div>
  )
}
