import { useState, useEffect, useRef } from 'react'
import { api } from '../api/client'

type Target =
  | { type: 'task'; projectId: string }
  | { type: 'project'; name: string }
  | { type: 'list-item'; listId: string; projectId: string }

type Props = {
  itemText: string
  onConvert: (target: Target) => void
  onClose: () => void
}

export function ConvertPicker({ itemText, onConvert, onClose }: Props) {
  const [tab, setTab] = useState<'project' | 'list'>('project')
  const [projects, setProjects] = useState<any[]>([])
  const [lists, setLists] = useState<any[]>([])
  const [query, setQuery] = useState('')
  const [newName, setNewName] = useState('')
  const inputRef = useRef<HTMLInputElement>(null)

  useEffect(() => {
    api.projects.list().then(d => setProjects(d.projects))
    api.lists.list().then(d => setLists(d.lists))
    inputRef.current?.focus()
  }, [])

  const filtered = tab === 'project'
    ? projects.filter(p => p.name.toLowerCase().includes(query.toLowerCase()))
    : lists.filter(l => l.name.toLowerCase().includes(query.toLowerCase()))

  const selectProject = (projectId: string) => {
    onConvert({ type: 'task', projectId })
  }

  const selectList = (listId: string) => {
    // Need a project to create the task in — use the first active project or create one
    const activeProject = projects.find(p => p.status === 'IN_PROGRESS') || projects[0]
    if (!activeProject) {
      alert('Create a project first — tasks need to belong to a project.')
      return
    }
    onConvert({ type: 'list-item', listId, projectId: activeProject.id })
  }

  const createNew = async () => {
    const name = newName.trim() || query.trim()
    if (!name) return
    if (tab === 'project') {
      onConvert({ type: 'project', name })
    } else {
      await api.lists.create(name)
      const d = await api.lists.list()
      setLists(d.lists)
      setNewName('')
      setQuery('')
    }
  }

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center" onClick={onClose}>
      <div className="absolute inset-0 bg-black/20" />
      <div className="relative bg-[var(--bg-card)] rounded-2xl border border-[var(--border)] w-96 max-h-[70vh] flex flex-col" style={{ boxShadow: 'var(--shadow-float)' }} onClick={e => e.stopPropagation()}>
        {/* Header */}
        <div className="p-4 border-b border-[var(--border)]">
          <div className="flex items-center justify-between mb-3">
            <h3 className="font-semibold text-sm">Move to...</h3>
            <button onClick={onClose} className="text-[var(--text-muted)] hover:text-[var(--text)] text-sm">✕</button>
          </div>
          <p className="text-xs text-[var(--text-muted)] mb-3 truncate">"{itemText}"</p>
          {/* Tabs */}
          <div className="flex gap-1 bg-[var(--bg-surface)] rounded-lg p-0.5">
            <button onClick={() => setTab('project')}
              className={`flex-1 text-xs py-1.5 rounded-md font-medium transition-colors ${tab === 'project' ? 'bg-[var(--bg-card)] shadow-sm text-[var(--text)]' : 'text-[var(--text-muted)]'}`}>
              Project (as task)
            </button>
            <button onClick={() => setTab('list')}
              className={`flex-1 text-xs py-1.5 rounded-md font-medium transition-colors ${tab === 'list' ? 'bg-[var(--bg-card)] shadow-sm text-[var(--text)]' : 'text-[var(--text-muted)]'}`}>
              List
            </button>
          </div>
        </div>

        {/* Search */}
        <div className="px-4 pt-3">
          <input ref={inputRef} value={query} onChange={e => setQuery(e.target.value)}
            placeholder={tab === 'project' ? 'Search projects...' : 'Search lists...'}
            onKeyDown={e => { if (e.key === 'Escape') onClose() }}
            className="w-full px-3 py-2 border border-[var(--border)] rounded-lg outline-none text-sm" />
        </div>

        {/* Results */}
        <div className="flex-1 overflow-y-auto p-2 min-h-0">
          {filtered.map(item => (
            <button key={item.id}
              onClick={() => tab === 'project' ? selectProject(item.id) : selectList(item.id)}
              className="w-full text-left px-3 py-2 rounded-lg text-sm hover:bg-[var(--bg)] transition-colors flex items-center gap-2">
              <span className="text-base">{tab === 'project' ? '📋' : '📌'}</span>
              <span className="flex-1 truncate">{item.name}</span>
              {tab === 'list' && <span className="text-[10px] text-[var(--text-muted)]">{item.type}</span>}
            </button>
          ))}
          {filtered.length === 0 && query && (
            <p className="text-xs text-[var(--text-muted)] text-center py-4">No matches</p>
          )}
        </div>

        {/* Create new */}
        <div className="p-3 border-t border-[var(--border)]">
          <button onClick={createNew}
            className="w-full text-left px-3 py-2 rounded-lg text-sm hover:bg-[var(--accent-sage-light)] text-[var(--accent-sage)] transition-colors flex items-center gap-2">
            <span>+</span>
            <span>
              {tab === 'project'
                ? `Create project "${query.trim() || itemText}"`
                : `Create list "${query.trim() || 'New List'}"`}
            </span>
          </button>
        </div>
      </div>
    </div>
  )
}
