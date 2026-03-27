import { useState, useEffect } from 'react'
import { api } from '../api/client'

const today = () => new Date().toISOString().slice(0, 10)

const getDays = (offset: number) => {
  const center = new Date()
  center.setDate(center.getDate() + offset)
  return Array.from({ length: 7 }, (_, i) => {
    const d = new Date(center)
    d.setDate(center.getDate() + i - 3)
    return d.toISOString().slice(0, 10)
  })
}

const formatDay = (d: string) => {
  const dt = new Date(d + 'T00:00:00')
  return { weekday: dt.toLocaleDateString('en', { weekday: 'short' }), day: dt.getDate() }
}

export function HabitsPage() {
  const [habits, setHabits] = useState<any[]>([])
  const [entries, setEntries] = useState<Record<string, Record<string, string>>>({})
  const [adding, setAdding] = useState(false)
  const [name, setName] = useState('')
  const [loading, setLoading] = useState(true)
  const [offset, setOffset] = useState(0)

  const days = getDays(offset)
  const from = days[0]
  const to = days[6]

  useEffect(() => { load() }, [offset])

  const load = async () => {
    setLoading(true)
    const d = await api.habits.list()
    setHabits(d.habits)
    const ent: Record<string, Record<string, string>> = {}
    for (const h of d.habits) {
      const e = await api.habits.getEntries(h.id, from, to)
      ent[h.id] = {}
      for (const entry of e.entries) ent[h.id][entry.date] = entry.value
    }
    setEntries(ent)
    setLoading(false)
  }

  const add = async (e: React.FormEvent) => {
    e.preventDefault()
    if (!name.trim()) return
    await api.habits.create(name.trim())
    setName(''); setAdding(false); load()
  }

  const toggle = async (habitId: string, date: string) => {
    const current = entries[habitId]?.[date]
    const next = !current ? 'YES' : current === 'YES' ? 'NO' : current === 'NO' ? 'SKIP' : 'YES'
    await api.habits.createEntry(habitId, date, next)
    setEntries(prev => ({ ...prev, [habitId]: { ...prev[habitId], [date]: next } }))
    const d = await api.habits.list()
    setHabits(d.habits)
  }

  const deleteHabit = async (id: string) => {
    if (!confirm('Delete this habit and all entries?')) return
    await api.habits.delete(id)
    load()
  }

  const cellColor = (value?: string) => {
    if (value === 'YES') return 'bg-[var(--accent-sage)] text-white'
    if (value === 'NO') return 'bg-[var(--danger)] text-white'
    if (value === 'SKIP') return 'bg-[var(--border)] text-[var(--text-muted)]'
    return 'bg-gray-100 text-[var(--text-muted)]'
  }

  const cellLabel = (value?: string) => {
    if (value === 'YES') return '✓'
    if (value === 'NO') return '✗'
    if (value === 'SKIP') return '–'
    return ''
  }

  const todayStr = today()

  return (
    <div>
      <div className="flex items-center justify-between mb-6">
        <h1 className="text-2xl font-semibold">Habits</h1>
        <button onClick={() => setAdding(true)} className="px-3 py-1.5 bg-[var(--accent-sage)] text-white rounded-lg text-sm font-medium hover:opacity-90">+ New</button>
      </div>
      {adding && (
        <form onSubmit={add} className="mb-4 flex gap-2">
          <input value={name} onChange={e => setName(e.target.value)} placeholder="Habit name..." autoFocus
            onKeyDown={e => e.key === 'Escape' && (setAdding(false), setName(''))}
            className="flex-1 px-3 py-2 border border-[var(--border)] rounded-lg outline-none text-sm" />
          <button type="submit" className="px-3 py-2 bg-[var(--accent-sage)] text-white rounded-lg text-sm">Add</button>
          <button type="button" onClick={() => { setAdding(false); setName('') }} className="px-3 py-2 text-sm text-[var(--text-muted)]">Cancel</button>
        </form>
      )}

      {/* Legend */}
      <div className="flex gap-4 mb-4 text-[10px] text-[var(--text-muted)]">
        <span className="flex items-center gap-1"><span className="w-3 h-3 rounded bg-[var(--accent-sage)] inline-block" /> Done</span>
        <span className="flex items-center gap-1"><span className="w-3 h-3 rounded bg-[var(--danger)] inline-block" /> Missed</span>
        <span className="flex items-center gap-1"><span className="w-3 h-3 rounded bg-[var(--border)] inline-block" /> Skipped</span>
      </div>

      {loading && habits.length === 0 && <p className="text-[var(--text-muted)] text-center py-12">Loading...</p>}

      {habits.length > 0 && (
        <div className="bg-white rounded-xl border border-[var(--border)] overflow-hidden">
          {/* Day headers with navigation */}
          <div className="flex items-center border-b border-[var(--border)]">
            <div className="w-48 flex-shrink-0 px-4 py-2">
              <button onClick={() => setOffset(0)} className="text-[10px] text-[var(--accent-sage)] hover:underline">
                {offset !== 0 ? 'Back to today' : ''}
              </button>
            </div>
            <button onClick={() => setOffset(offset - 7)} className="px-2 py-2 text-[var(--text-muted)] hover:text-[var(--text)] text-sm">←</button>
            <div className="flex flex-1">
              {days.map(d => {
                const { weekday, day } = formatDay(d)
                const isToday = d === todayStr
                return (
                  <div key={d} className={`flex-1 text-center py-2 ${isToday ? 'bg-[var(--accent-sage-light)]' : ''}`}>
                    <div className={`text-[10px] ${isToday ? 'text-[var(--accent-sage)] font-bold' : 'text-[var(--text-muted)]'}`}>{weekday}</div>
                    <div className={`text-sm font-medium ${isToday ? 'text-[var(--accent-sage)]' : 'text-[var(--text)]'}`}>{day}</div>
                  </div>
                )
              })}
            </div>
            <button onClick={() => setOffset(offset + 7)} className="px-2 py-2 text-[var(--text-muted)] hover:text-[var(--text)] text-sm">→</button>
          </div>

          {/* Habit rows */}
          {habits.map(h => (
            <div key={h.id} className="flex items-center border-b border-[var(--border)] last:border-b-0 group hover:bg-[var(--bg)] transition-colors">
              <div className="w-48 flex-shrink-0 px-4 py-3">
                <div className="flex items-center justify-between">
                  <span className="font-medium text-sm truncate">{h.name}</span>
                  <button onClick={() => deleteHabit(h.id)}
                    className="text-[var(--text-muted)] hover:text-[var(--danger)] text-xs opacity-0 group-hover:opacity-100 ml-1">✕</button>
                </div>
                <span className="text-[10px] text-[var(--text-muted)]">
                  <span className="font-semibold text-[var(--text)]">{h.currentStreak}</span> day streak · Best: {h.longestStreak}
                </span>
              </div>
              <div className="w-6" /> {/* spacer for left arrow */}
              <div className="flex flex-1">
                {days.map(d => {
                  const val = entries[h.id]?.[d]
                  const isToday = d === todayStr
                  return (
                    <div key={d} className={`flex-1 flex items-center justify-center py-3 ${isToday ? 'bg-[var(--accent-sage-light)]' : ''}`}>
                      <button onClick={() => toggle(h.id, d)}
                        title={`${d}: ${val || 'none'} (click to cycle)`}
                        className={`w-9 h-9 rounded-lg flex items-center justify-center text-sm font-medium transition-all hover:scale-110 ${cellColor(val)}`}>
                        {cellLabel(val)}
                      </button>
                    </div>
                  )
                })}
              </div>
              <div className="w-6" /> {/* spacer for right arrow */}
            </div>
          ))}
        </div>
      )}

      {!loading && habits.length === 0 && !adding && (
        <div className="text-center py-16">
          <div className="text-4xl mb-3">✅</div>
          <p className="text-[var(--text-muted)]">No habits yet</p>
          <p className="text-xs text-[var(--text-muted)] mt-1">Start tracking a daily habit</p>
        </div>
      )}
    </div>
  )
}
