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
  const [movingTaskId, setMovingTaskId] = useState<string | null>(null)

  // Waiting For state
  const [wfItems, setWfItems] = useState<any[]>([])
  const [wfAdding, setWfAdding] = useState(false)
  const [wfDesc, setWfDesc] = useState('')
  const [wfPerson, setWfPerson] = useState('')
  const [wfDate, setWfDate] = useState('')

  const isWaitingFor = selectedList?.name === 'Waiting For'

  useEffect(() => {
    load()
    api.projects.list().then(d => setProjects(d.projects))
  }, [])

  const load = async () => {
    setLoading(true)
    const d = await api.lists.list()
    setLists(d.lists)
    setLoading(false)
    if (!selectedList && d.lists.length > 0) {
      openList(d.lists[0])
    }
  }

  const openList = async (list: any) => {
    setSelectedList(list)
    setMovingTaskId(null)
    if (list.name === 'Waiting For') {
      const d = await api.waitingFor.list()
      setWfItems(d.items)
    } else {
      const d = await api.lists.getItems(list.id)
      setItems(d.items || [])
    }
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
    load()
  }

  const addTaskToList = async (e: React.FormEvent) => {
    e.preventDefault()
    if (!newTask.trim() || !selectedList) return
    const project = projects.find(p => p.status === 'IN_PROGRESS') || projects[0]
    if (!project) {
      alert('Create a project first — tasks need to belong to a project.')
      return
    }
    const task = await api.tasks.create({ projectId: project.id, title: newTask.trim() })
    await api.lists.addItem(selectedList.id, task.id)
    setNewTask('')
    openList(selectedList)
    load()
  }

  const toggleTask = async (task: any) => {
    await api.tasks.update(task.id, { projectId: task.projectId, done: !task.done })
    openList(selectedList)
  }

  const moveToInbox = async (taskTitle: string, taskId: string) => {
    await api.inbox.create(taskTitle)
    await api.lists.removeItem(selectedList.id, taskId)
    setMovingTaskId(null)
    openList(selectedList)
    load()
  }

  const moveToList = async (targetListId: string, taskId: string) => {
    await api.lists.addItem(targetListId, taskId)
    await api.lists.removeItem(selectedList.id, taskId)
    setMovingTaskId(null)
    openList(selectedList)
    load()
  }

  // Waiting For actions
  const addWfItem = async (e: React.FormEvent) => {
    e.preventDefault()
    if (!wfDesc.trim() || !wfPerson.trim() || !wfDate) return
    try {
      await api.waitingFor.create(wfDesc.trim(), wfPerson.trim(), wfDate)
      setWfDesc(''); setWfPerson(''); setWfDate(''); setWfAdding(false)
      openList(selectedList)
      load()
    } catch (err: any) {
      alert(err.message || 'Failed to add item')
    }
  }

  const acknowledgeWf = async (id: string) => {
    await api.waitingFor.acknowledge(id)
    openList(selectedList)
    load()
  }

  const deleteWf = async (id: string) => {
    if (!confirm('Delete this item?')) return
    await api.waitingFor.delete(id)
    openList(selectedList)
    load()
  }

  const otherLists = lists.filter(l => l.id !== selectedList?.id && l.name !== 'Waiting For')

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
                <span>{l.type === 'SYSTEM' ? (l.name === 'Today' ? '☀️' : l.name === 'Waiting For' ? '⏳' : '💭') : '📌'}</span>
                <span className="truncate">{l.name}</span>
                {l.name === 'Waiting For' && l.overdueCount > 0 && (
                  <span className="w-2 h-2 rounded-full bg-red-500 flex-shrink-0" />
                )}
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
          isWaitingFor ? (
            /* ── Waiting For view ── */
            <>
              <div className="flex items-center justify-between mb-4">
                <div className="flex items-center gap-2">
                  <h2 className="text-xl font-semibold">Waiting For</h2>
                  <span className="text-xs text-[var(--text-muted)]">{wfItems.length} items</span>
                </div>
                <button onClick={() => setWfAdding(!wfAdding)} className="text-sm text-[var(--accent-sage)] hover:underline">
                  {wfAdding ? 'Cancel' : '+ New'}
                </button>
              </div>
              {wfAdding && (
                <form onSubmit={addWfItem} className="mb-4 bg-white border border-[var(--border)] rounded-xl p-4 space-y-3">
                  <input value={wfDesc} onChange={e => setWfDesc(e.target.value)} placeholder="What are you waiting for?"
                    className="w-full px-3 py-2 border border-[var(--border)] rounded-lg outline-none text-sm" autoFocus />
                  <div className="flex gap-3">
                    <input value={wfPerson} onChange={e => setWfPerson(e.target.value)} placeholder="From whom?"
                      className="flex-1 px-3 py-2 border border-[var(--border)] rounded-lg outline-none text-sm" />
                    <input type="date" value={wfDate} onChange={e => setWfDate(e.target.value)}
                      className="px-3 py-2 border border-[var(--border)] rounded-lg outline-none text-sm" />
                  </div>
                  <button type="submit" className="px-4 py-2 bg-[var(--accent-sage)] text-white rounded-lg text-sm hover:opacity-90">Add</button>
                </form>
              )}
              <div className="space-y-1">
                {wfItems.map(item => (
                  <div key={item.id} className="flex items-center gap-3 bg-white px-4 py-3 rounded-xl border border-[var(--border)] group">
                    {item.overdue && (
                      <span className="w-2.5 h-2.5 rounded-full bg-red-500 flex-shrink-0" title="Overdue" />
                    )}
                    <div className="flex-1 min-w-0">
                      <div className="text-sm">{item.description}</div>
                      <div className="text-xs text-[var(--text-muted)] mt-0.5">
                        From <span className="font-medium">{item.waitingFor}</span> · Due {item.dueDate}
                        {item.acknowledged && <span className="ml-1 text-[var(--accent-sage)]">✓ acknowledged</span>}
                      </div>
                    </div>
                    <div className="flex items-center gap-2">
                      {item.overdue && (
                        <button onClick={() => acknowledgeWf(item.id)}
                          className="text-xs px-2 py-1 rounded bg-[var(--accent-sage-light)] text-[var(--accent-sage)] hover:opacity-80">
                          Acknowledge
                        </button>
                      )}
                      <button onClick={() => deleteWf(item.id)}
                        className="text-[var(--text-muted)] hover:text-[var(--danger)] text-xs opacity-0 group-hover:opacity-100">✕</button>
                    </div>
                  </div>
                ))}
                {wfItems.length === 0 && (
                  <div className="text-center py-12">
                    <p className="text-[var(--text-muted)] text-sm">Nothing waiting</p>
                    <p className="text-xs text-[var(--text-muted)] mt-1">Add items you're waiting on from others</p>
                  </div>
                )}
              </div>
            </>
          ) : (
            /* ── Regular list view ── */
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
                  <div key={item.taskId} className="relative flex items-center gap-3 bg-white px-4 py-2.5 rounded-xl border border-[var(--border)] group">
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
                    <div className="flex items-center gap-1 opacity-0 group-hover:opacity-100 transition-opacity">
                      {item.task && (
                        <button onClick={() => setMovingTaskId(movingTaskId === item.taskId ? null : item.taskId)}
                          className="text-[10px] px-2 py-1 rounded-md bg-[var(--accent-sage-light)] text-[var(--accent-sage)] hover:bg-[var(--accent-sage)] hover:text-white transition-colors">
                          Move →
                        </button>
                      )}
                      <button onClick={() => removeItem(item.taskId)}
                        className="text-[var(--text-muted)] hover:text-[var(--danger)] text-xs">✕</button>
                    </div>

                    {/* Move dropdown */}
                    {movingTaskId === item.taskId && item.task && (
                      <div className="absolute right-0 top-full mt-1 z-10 bg-white border border-[var(--border)] rounded-lg shadow-lg py-1 min-w-[160px]">
                        <button onClick={() => moveToInbox(item.task.title, item.taskId)}
                          className="w-full text-left px-3 py-1.5 text-sm hover:bg-[var(--bg)] transition-colors">
                          📥 Inbox
                        </button>
                        {otherLists.map(l => (
                          <button key={l.id} onClick={() => moveToList(l.id, item.taskId)}
                            className="w-full text-left px-3 py-1.5 text-sm hover:bg-[var(--bg)] transition-colors">
                            {l.type === 'SYSTEM' ? (l.name === 'Today' ? '☀️' : '💭') : '📌'} {l.name}
                          </button>
                        ))}
                      </div>
                    )}
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
          )
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
