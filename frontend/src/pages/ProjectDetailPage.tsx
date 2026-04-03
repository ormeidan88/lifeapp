import { useState, useEffect } from 'react'
import { api } from '../api/client'
import { PageEditor } from '../components/editor/PageEditor'

export function ProjectDetailPage({ projectId, onBack }: { projectId: string; onBack: () => void }) {
  const [project, setProject] = useState<any>(null)
  const [tasks, setTasks] = useState<any[]>([])
  const [page, setPage] = useState<any>(null)
  const [newTask, setNewTask] = useState('')
  const [tab, setTab] = useState<'tasks' | 'notes'>('tasks')
  const [dragIdx, setDragIdx] = useState<number | null>(null)

  useEffect(() => { load() }, [projectId])

  const load = async () => {
    const p = await api.projects.get(projectId)
    setProject(p)
    const t = await api.tasks.list(projectId)
    setTasks(t.tasks)
    if (p.pageId) { const pg = await api.pages.get(p.pageId); setPage(pg) }
  }

  const addTask = async (e: React.FormEvent) => {
    e.preventDefault()
    if (!newTask.trim()) return
    await api.tasks.create({ projectId, title: newTask.trim() })
    setNewTask(''); load()
  }

  const toggleTask = async (task: any) => {
    await api.tasks.update(task.id, { projectId, done: !task.done })
    load()
  }

  const deleteTask = async (task: any) => {
    await api.tasks.delete(task.id, projectId)
    load()
  }

  const deleteProject = async () => {
    if (!confirm('Delete this project and all its tasks?')) return
    await api.projects.delete(projectId)
    onBack()
  }

  const handleDrop = async (dropIdx: number) => {
    if (dragIdx === null || dragIdx === dropIdx) return
    const reordered = [...tasks]
    const [moved] = reordered.splice(dragIdx, 1)
    reordered.splice(dropIdx, 0, moved)
    setTasks(reordered)
    await api.tasks.reorder(projectId, reordered.map(t => t.id))
    setDragIdx(null)
  }

  const savePage = async (content: any) => {
    if (page?.id) await api.pages.update(page.id, { content })
  }

  const updateStatus = async (status: string) => {
    await api.projects.update(projectId, { status })
    load()
  }

  if (!project) return <p className="text-[var(--text-muted)]">Loading...</p>

  return (
    <div>
      <div className="flex items-center justify-between mb-4">
        <button onClick={onBack} className="text-[var(--text-muted)] hover:text-[var(--text)]">← Back</button>
        <button onClick={deleteProject} className="text-xs text-[var(--danger)] hover:underline">Delete project</button>
      </div>
      <div className="flex items-center gap-3 mb-4">
        <h1 className="text-2xl font-semibold">{project.name}</h1>
        <select value={project.status} onChange={e => updateStatus(e.target.value)}
          className="text-sm border border-[var(--border)] rounded-lg px-2.5 py-1.5 bg-[var(--bg-card)] outline-none"
          style={{ color: 'var(--text)' }}>
          {['NOT_STARTED', 'IN_PROGRESS', 'DONE', 'CANCELLED'].map(s => (
            <option key={s} value={s}>
              {{ NOT_STARTED: 'Not started', IN_PROGRESS: 'In progress', DONE: 'Done', CANCELLED: 'Cancelled' }[s]}
            </option>
          ))}
        </select>
      </div>
      <div className="flex gap-4 mb-4 border-b border-[var(--border)]">
        {(['tasks', 'notes'] as const).map(t => (
          <button key={t} onClick={() => setTab(t)}
            className={`pb-2 px-1 capitalize ${tab === t ? 'border-b-2 border-[var(--accent-sage)] text-[var(--text)]' : 'text-[var(--text-muted)]'}`}>
            {t}
          </button>
        ))}
      </div>
      {/* Tasks tab */}
      <div style={{ display: tab === 'tasks' ? 'block' : 'none' }}>
        <form onSubmit={addTask} className="mb-4">
          <input value={newTask} onChange={e => setNewTask(e.target.value)} placeholder="Add a task..."
            className="w-full px-3 py-2 border border-[var(--border)] rounded-lg outline-none focus:border-[var(--accent-sage)]" />
        </form>
        <div className="space-y-1">
          {tasks.map((t, i) => (
            <div key={t.id}
              draggable
              onDragStart={() => setDragIdx(i)}
              onDragOver={e => e.preventDefault()}
              onDrop={() => handleDrop(i)}
              className="flex items-center gap-3 bg-[var(--bg-card)] px-3 py-2 rounded-lg border border-[var(--border)] group cursor-grab active:cursor-grabbing">
              <span className="text-[var(--text-muted)] opacity-0 group-hover:opacity-100 cursor-grab">⠿</span>
              <input type="checkbox" checked={t.done} onChange={() => toggleTask(t)} className="w-4 h-4 accent-[var(--accent-sage)]" />
              <span className={`flex-1 ${t.done ? 'line-through text-[var(--text-muted)]' : ''}`}>{t.title}</span>
              <button onClick={() => deleteTask(t)} className="text-[var(--text-muted)] hover:text-[var(--danger)] text-sm opacity-0 group-hover:opacity-100">✕</button>
            </div>
          ))}
        </div>
        {tasks.length === 0 && <p className="text-[var(--text-muted)] text-center py-6">No tasks yet</p>}
      </div>
      {/* Notes tab — always mounted to preserve undo history */}
      <div style={{ display: tab === 'notes' ? 'block' : 'none' }}>
        {page && <PageEditor content={page.content} onUpdate={savePage} />}
      </div>
    </div>
  )
}
