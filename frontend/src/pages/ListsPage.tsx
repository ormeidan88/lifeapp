import { useState, useEffect } from 'react'
import { api } from '../api/client'

export function ListsPage() {
  const [lists, setLists] = useState<any[]>([])
  const [selectedList, setSelectedList] = useState<any>(null)
  const [items, setItems] = useState<any[]>([])
  const [adding, setAdding] = useState(false)
  const [name, setName] = useState('')
  const [loading, setLoading] = useState(true)
  const [newTask, setNewTask] = useState('')
  const [projects, setProjects] = useState<any[]>([])

  useEffect(() => {
    load()
    api.projects.list().then(d => setProjects(d.projects))
  }, [])

  const load = async () => {
    setLoading(true)
    const d = await api.lists.list()
    setLists(d.lists)
    setLoading(false)
    // Auto-select first list if none selected
    if (!selectedList && d.lists.length > 0) {
      openList(d.lists[0])
    }
  }

  const openList = async (list: any) => {
    setSelectedList(list)
    const d = await api.lists.getItems(list.id)
    setItems(d.items || [])
  }

  const addList = async (e: React.FormEvent) => {
    e.preventDefault()
    if (!name.trim()) return
    await api.lists.create(name.trim())
    setName(''); setAdding(false); load()
  }

  const deleteList = async (id: string) => {
    if (!confirm('Delete this list?')) return
    await api.lists.delete(id)
    if (selectedList?.id === id) { setSelectedList(null); setItems([]) }
    load()
  }

  const removeItem = async (taskId: string) => {
    if (!selectedList) return
    await api.lists.removeItem(selectedList.id, taskId)
    openList(selectedList)
  }

  const addTaskToList = async (e: React.FormEvent) => {
    e.preventDefault()
    if (!newTask.trim() || !selectedList) return
    // Need a project to hold the task — use first active or first available
    const project = projects.find(p => p.status === 'IN_PROGRESS') || projects[0]
    if (!project) {
      alert('Create a project first — tasks need to belong to a project.')
      return
    }
    const task = await api.tasks.create({ projectId: project.id, title: newTask.trim() })
    await api.lists.addItem(selectedList.id, task.id)
    setNewTask('')
    openList(selectedList)
  }

  const toggleTask = async (task: any) => {
    await api.tasks.update(task.id, { projectId: task.projectId, done: !task.done })
    openList(selectedList)
  }

  return (
    <div className="flex gap-6 min-h-[60vh]">
      {/* List sidebar */}
      <div className="w-56 flex-shrink-0">
        <div className="flex items-center justify-between mb-4">
          <h1 className="text-2xl font-semibold">Lists</h1>
          <button onClick={() => setAdding(true)} className="text-sm text-[var(--accent-sage)] hover:underline">+ New</button>
        </div>
        {adding && (
          <form onSubmit={addList} className="mb-3">
            <input value={name} onChange={e => setName(e.target.value)} placeholder="List name..." autoFocus
              onKeyDown={e => e.key === 'Escape' && (setAdding(false), setName(''))}
              className="w-full px-3 py-2 border border-[var(--border)] rounded-lg outline-none text-sm" />
          </form>
        )}
        {loading && lists.length === 0 && <p className="text-xs text-[var(--text-muted)]">Loading...</p>}
        <div className="space-y-0.5">
          {lists.map(l => (
            <div key={l.id}
              onClick={() => openList(l)}
              className={`flex items-center justify-between px-3 py-2 rounded-lg cursor-pointer group text-sm transition-colors
                ${selectedList?.id === l.id ? 'bg-[var(--accent-sage-light)] text-[var(--text)]' : 'hover:bg-[var(--bg)] text-[var(--text-muted)]'}`}>
              <div className="flex items-center gap-2 min-w-0">
                <span>{l.type === 'SYSTEM' ? (l.name === 'Today' ? '☀️' : '💭') : '📌'}</span>
                <span className="truncate">{l.name}</span>
              </div>
              <div className="flex items-center gap-1">
                <span className="text-[10px] text-[var(--text-muted)]">{l.itemCount}</span>
                {l.type !== 'SYSTEM' && (
                  <button onClick={e => { e.stopPropagation(); deleteList(l.id) }}
                    className="text-[var(--text-muted)] hover:text-[var(--danger)] text-xs opacity-0 group-hover:opacity-100">✕</button>
                )}
              </div>
            </div>
          ))}
        </div>
      </div>

      {/* List content */}
      <div className="flex-1 min-w-0">
        {selectedList ? (
          <>
            <div className="flex items-center gap-2 mb-4">
              <h2 className="text-xl font-semibold">{selectedList.name}</h2>
              <span className="text-xs text-[var(--text-muted)]">{items.length} items</span>
            </div>
            <form onSubmit={addTaskToList} className="mb-4">
              <input value={newTask} onChange={e => setNewTask(e.target.value)}
                placeholder="Add a task to this list..."
                className="w-full px-3 py-2 border border-[var(--border)] rounded-lg outline-none text-sm" />
            </form>
            <div className="space-y-1">
              {items.map(item => (
                <div key={item.taskId} className="flex items-center gap-3 bg-white px-4 py-2.5 rounded-xl border border-[var(--border)] group">
                  {item.task ? (
                    <>
                      <input type="checkbox" checked={item.task.done} onChange={() => toggleTask(item.task)} />
                      <span className={`flex-1 text-sm ${item.task.done ? 'line-through text-[var(--text-muted)]' : ''}`}>
                        {item.task.title}
                      </span>
                    </>
                  ) : (
                    <span className="flex-1 text-sm text-[var(--text-muted)]">Task not found</span>
                  )}
                  <button onClick={() => removeItem(item.taskId)}
                    className="text-[var(--text-muted)] hover:text-[var(--danger)] text-xs opacity-0 group-hover:opacity-100">Remove</button>
                </div>
              ))}
              {items.length === 0 && (
                <div className="text-center py-12">
                  <p className="text-[var(--text-muted)] text-sm">This list is empty</p>
                  <p className="text-xs text-[var(--text-muted)] mt-1">Move items here from the Inbox</p>
                </div>
              )}
            </div>
          </>
        ) : (
          <div className="text-center py-16">
            <div className="text-4xl mb-3">📌</div>
            <p className="text-[var(--text-muted)]">Select a list</p>
          </div>
        )}
      </div>
    </div>
  )
}
