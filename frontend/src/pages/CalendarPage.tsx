import { useState, useEffect, useRef } from 'react'
import { api } from '../api/client'
import { DailyNotePanel } from '../components/calendar/DailyNotePanel'

const weekDays = ['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun']
const hours = Array.from({ length: 17 }, (_, i) => i + 6) // 6am-10pm

const getWeekDates = (offset: number) => {
  const d = new Date()
  const day = d.getDay()
  // getDay() returns 0 for Sunday — adjust so Monday is start of week
  const diff = day === 0 ? -6 : 1 - day
  d.setDate(d.getDate() + diff + offset * 7)
  return Array.from({ length: 7 }, (_, i) => {
    const day = new Date(d)
    day.setDate(d.getDate() + i)
    return day.toISOString().slice(0, 10)
  })
}

const getDayDate = (offset: number) => {
  const d = new Date()
  d.setDate(d.getDate() + offset)
  return d.toISOString().slice(0, 10)
}

export function CalendarPage() {
  const [events, setEvents] = useState<any[]>([])
  const [weekOffset, setWeekOffset] = useState(0)
  const [dayOffset, setDayOffset] = useState(0)
  const [view, setView] = useState<'weekly' | 'daily'>('weekly')
  const [todayTasks, setTodayTasks] = useState<any[]>([])
  const [todayListId, setTodayListId] = useState<string | null>(null)
  const [habits, setHabits] = useState<any[]>([])
  const [habitEntries, setHabitEntries] = useState<Record<string, string>>({})
  const [adding, setAdding] = useState<{ date: string; startTime: string } | null>(null)
  const [title, setTitle] = useState('')
  const [endTime, setEndTime] = useState('')
  const [newNotes, setNewNotes] = useState('')
  const [dragging, setDragging] = useState<string | null>(null)
  const [draggingTask, setDraggingTask] = useState<{ id: string; title: string } | null>(null)
  const [dropTarget, setDropTarget] = useState<string | null>(null) // "${date}-${hour}"
  const [editingEvent, setEditingEvent] = useState<any | null>(null)
  const [editNotes, setEditNotes] = useState('')
  const dragTarget = useRef<{ date: string; hour: number } | null>(null)

  const dates = view === 'weekly' ? getWeekDates(weekOffset) : [getDayDate(dayOffset)]
  const from = dates[0]
  const to = dates[dates.length - 1]

  // The "selected date" for the side panel — today in weekly view, the viewed day in daily view
  const selectedDate = view === 'daily' ? getDayDate(dayOffset) : new Date().toISOString().slice(0, 10)

  useEffect(() => {
    let ignore = false
    const run = async () => {
      try {
        const d = await api.calendar.list(from, to)
        if (!ignore) setEvents(d.events || [])
      } catch {
        if (!ignore) setEvents([])
      }
    }
    run()
    return () => { ignore = true }
  }, [weekOffset, dayOffset, view])

  useEffect(() => {
    let ignore = false
    const run = async () => {
      try {
        // Tasks
        const lists = await api.lists.list()
        if (ignore) return
        const today = lists.lists?.find((l: any) => l.name === 'Today')
        if (today) {
          setTodayListId(today.id)
          const items = await api.lists.getItems(today.id)
          if (ignore) return
          setTodayTasks(items.items || [])
        }

        // Habits
        const d = await api.habits.list()
        if (ignore) return
        setHabits(d.habits || [])
        const ent: Record<string, string> = {}
        for (const h of d.habits || []) {
          if (ignore) break
          try {
            const e = await api.habits.getEntries(h.id, selectedDate, selectedDate)
            if (!ignore && e.entries?.length > 0) ent[h.id] = e.entries[0].value
          } catch { /* skip individual habit errors */ }
        }
        if (!ignore) setHabitEntries(ent)
      } catch {
        if (!ignore) { setTodayTasks([]); setHabits([]); setHabitEntries({}) }
      }
    }
    run()
    return () => { ignore = true }
  }, [selectedDate])

  const reloadEvents = async () => {
    try { const d = await api.calendar.list(from, to); setEvents(d.events || []) } catch { setEvents([]) }
  }

  const reloadTasks = async () => {
    try {
      const lists = await api.lists.list()
      const today = lists.lists?.find((l: any) => l.name === 'Today')
      if (today) {
        setTodayListId(today.id)
        const items = await api.lists.getItems(today.id)
        setTodayTasks(items.items || [])
      }
    } catch { setTodayTasks([]) }
  }

  const toggleTodayTask = async (task: any) => {
    await api.tasks.update(task.id, { projectId: task.projectId, done: !task.done })
    reloadTasks()
  }

  const toggleHabit = async (habitId: string, value: string) => {
    // If clicking the already-selected value, clear it (set to empty by cycling to next)
    const current = habitEntries[habitId]
    const newVal = current === value ? '' : value
    if (newVal) {
      await api.habits.createEntry(habitId, selectedDate, newVal)
      setHabitEntries(prev => ({ ...prev, [habitId]: newVal }))
    }
  }

  const addEvent = async (e: React.FormEvent) => {
    e.preventDefault()
    if (!title.trim() || !adding) return
    const et = endTime || `${String(parseInt(adding.startTime) + 1).padStart(2, '0')}:00`
    await api.calendar.create({ title: title.trim(), date: adding.date, startTime: adding.startTime, endTime: et, ...(newNotes.trim() ? { notes: newNotes.trim() } : {}) })
    setAdding(null); setTitle(''); setEndTime(''); setNewNotes(''); reloadEvents()
  }

  const handleDrop = async (date: string, hour: number) => {
    setDropTarget(null)
    if (draggingTask) {
      const startTime = `${String(hour).padStart(2, '0')}:00`
      const endTime = `${String(hour + 1).padStart(2, '0')}:00`
      await api.calendar.create({ title: draggingTask.title, date, startTime, endTime })
      setDraggingTask(null)
      reloadEvents()
    } else if (dragging) {
      await api.calendar.update(dragging, { date, startTime: `${String(hour).padStart(2, '0')}:00` })
      setDragging(null)
      reloadEvents()
    }
  }

  const deleteEvent = async (id: string) => {
    await api.calendar.delete(id)
    reloadEvents()
  }

  const openEventDetail = (ev: any, e: React.MouseEvent) => {
    e.stopPropagation()
    setEditingEvent(ev)
    setEditNotes(ev.notes || '')
  }

  const saveNotes = async () => {
    if (!editingEvent) return
    await api.calendar.update(editingEvent.id, { notes: editNotes })
    setEditingEvent(null)
    reloadEvents()
  }

  const eventsForDateHour = (date: string, hour: number) =>
    events.filter(e => e.date === date && e.startTime && parseInt(e.startTime) === hour)

  const getEventStyle = (ev: any) => {
    const startH = parseInt(ev.startTime)
    const startM = parseInt(ev.startTime?.split(':')[1] || '0')
    const endH = ev.endTime ? parseInt(ev.endTime) : startH + 1
    const endM = parseInt(ev.endTime?.split(':')[1] || '0')
    const topMin = startM
    const durationMin = (endH - startH) * 60 + (endM - startM)
    return {
      top: `${(topMin / 60) * 48}px`,
      height: `${Math.max((durationMin / 60) * 48, 20)}px`,
      left: '2px',
      right: '2px',
      position: 'absolute' as const,
      zIndex: 5,
    }
  }

  const todayStr = new Date().toISOString().slice(0, 10)
  const currentHour = new Date().getHours()
  const currentMinute = new Date().getMinutes()

  const nav = view === 'weekly'
    ? { prev: () => setWeekOffset(weekOffset - 1), today: () => setWeekOffset(0), next: () => setWeekOffset(weekOffset + 1) }
    : { prev: () => setDayOffset(dayOffset - 1), today: () => setDayOffset(0), next: () => setDayOffset(dayOffset + 1) }

  return (
    <div>
      <div className="flex items-center justify-between mb-6">
        <h1 className="text-2xl font-semibold">Calendar</h1>
        <div className="flex gap-2 items-center">
          <div className="flex border border-[var(--border)] rounded overflow-hidden text-sm">
            <button onClick={() => setView('daily')} className={`px-3 py-1 ${view === 'daily' ? 'bg-[var(--accent-sage)] text-white' : ''}`}>Day</button>
            <button onClick={() => setView('weekly')} className={`px-3 py-1 ${view === 'weekly' ? 'bg-[var(--accent-sage)] text-white' : ''}`}>Week</button>
          </div>
          <button onClick={nav.prev} className="px-3 py-1 border border-[var(--border)] rounded text-sm">←</button>
          <button onClick={nav.today} className="px-3 py-1 border border-[var(--border)] rounded text-sm">Today</button>
          <button onClick={nav.next} className="px-3 py-1 border border-[var(--border)] rounded text-sm">→</button>
        </div>
      </div>
      {adding && (
        <form onSubmit={addEvent} className="bg-[var(--bg-card)] p-4 rounded-lg border border-[var(--border)] mb-4 space-y-2">
          <div className="flex gap-2 items-end">
            <div className="flex-1">
              <label className="text-xs text-[var(--text-muted)]">{adding.date} at {adding.startTime}</label>
              <input value={title} onChange={e => setTitle(e.target.value)} placeholder="Event title..." autoFocus
                className="w-full px-3 py-2 border border-[var(--border)] rounded-lg outline-none focus:border-[var(--accent-sage)]" />
            </div>
            <input value={endTime} onChange={e => setEndTime(e.target.value)} placeholder="End (HH:mm)"
              className="w-28 px-3 py-2 border border-[var(--border)] rounded-lg outline-none" />
            <button type="submit" className="px-4 py-2 bg-[var(--accent-sage)] text-white rounded-lg text-sm">Add</button>
            <button type="button" onClick={() => { setAdding(null); setNewNotes('') }} className="px-3 py-2 text-sm text-[var(--text-muted)]">Cancel</button>
          </div>
          <textarea value={newNotes} onChange={e => setNewNotes(e.target.value)} placeholder="Notes"
            rows={2} className="w-full px-3 py-2 border border-[var(--border)] rounded-lg outline-none focus:border-[var(--accent-sage)] text-sm resize-none" />
        </form>
      )}
      <div className="flex gap-4">
        {/* Calendar grid */}
        <div className="flex-1 overflow-x-auto min-w-0">
        <div className={`grid ${view === 'weekly' ? 'min-w-[700px]' : 'min-w-[300px]'}`}
          style={{ gridTemplateColumns: `60px repeat(${dates.length}, 1fr)` }}>
          {/* Header */}
          <div className="text-xs text-[var(--text-muted)] p-1" />
          {dates.map((d, i) => {
            const isToday = d === todayStr
            const dayName = view === 'weekly' ? weekDays[i] : new Date(d).toLocaleDateString('en', { weekday: 'long' })
            return (
              <div key={d} className={`text-center text-xs p-1 ${isToday ? 'font-bold text-[var(--accent-sage)]' : 'text-[var(--text-muted)]'}`}>
                {dayName}<br />{d.slice(5)}
              </div>
            )
          })}
          {/* Time grid */}
          {hours.map(h => (
            <div key={`row-${h}`} className="contents">
              <div className="text-xs text-[var(--text-muted)] p-1 text-right pr-2 border-t border-[var(--border)]">
                {h}:00
              </div>
              {dates.map(d => (
                <div key={`${d}-${h}`}
                  onClick={() => setAdding({ date: d, startTime: `${String(h).padStart(2, '0')}:00` })}
                  onDragOver={e => { e.preventDefault(); dragTarget.current = { date: d, hour: h }; setDropTarget(`${d}-${h}`) }}
                  onDragLeave={() => setDropTarget(null)}
                  onDrop={() => handleDrop(d, h)}
                  className={`border-t border-l border-[var(--border)] min-h-[48px] p-0.5 cursor-pointer relative transition-colors
                    ${dropTarget === `${d}-${h}` && draggingTask ? 'bg-[var(--accent-sage-light)] ring-1 ring-inset ring-[var(--accent-sage)]' : d === todayStr ? 'bg-[var(--accent-sage-light)]' : 'hover:bg-[var(--bg-surface)]'}`}>
                  {eventsForDateHour(d, h).map(ev => (
                    <div key={ev.id}
                      draggable
                      onDragStart={(e) => { e.stopPropagation(); setDragging(ev.id) }}
                      onDragEnd={() => setDragging(null)}
                      onClick={(e) => openEventDetail(ev, e)}
                      style={{ ...getEventStyle(ev), ...(ev.color ? { backgroundColor: ev.color + '33', color: ev.color } : {}) }}
                      className={`text-xs px-1.5 py-0.5 rounded cursor-grab active:cursor-grabbing group/ev overflow-hidden ${!ev.color ? 'bg-[var(--accent-blue-light)] text-[var(--accent-blue)]' : ''}`}>
                      <span className="truncate block">{ev.title}</span>
                      {ev.notes && <span className="ml-1 opacity-50">✎</span>}
                      <button onClick={(e) => { e.stopPropagation(); deleteEvent(ev.id) }}
                        className="absolute -top-1 -right-1 w-4 h-4 bg-[var(--bg-card)] border border-[var(--border)] rounded-full text-[8px] text-[var(--danger)] opacity-0 group-hover/ev:opacity-100 flex items-center justify-center">✕</button>
                    </div>
                  ))}
                  {/* Current time indicator */}
                  {d === todayStr && h === currentHour && (
                    <div className="absolute left-0 right-0 border-t-2 border-[var(--accent-terracotta)] z-10 pointer-events-none"
                      style={{ top: `${(currentMinute / 60) * 100}%` }}>
                      <div className="w-2 h-2 rounded-full bg-[var(--accent-terracotta)] -mt-1 -ml-1" />
                    </div>
                  )}
                </div>
              ))}
            </div>
          ))}
        </div>
        </div>

        {/* Today's tasks panel — aligned with 6am line */}
        <div className={`${view === 'daily' ? 'w-80' : 'w-56'} flex-shrink-0 hidden lg:block`}>
          {/* Spacer matching the calendar header row height */}
          <div className="h-[38px]"></div>
          <div className="bg-[var(--bg-card)] rounded-xl border border-[var(--border)] overflow-hidden">
            <div className="px-4 py-2.5 border-b border-[var(--border)] bg-[var(--bg)]">
              <h3 className="font-semibold text-xs flex items-center justify-between">
                <span className="flex items-center gap-1.5">☀️ Today</span>
                <span className="text-[10px] text-[var(--text-muted)] font-normal">{todayTasks.filter(t => t.task && !t.task.done).length} left</span>
              </h3>
            </div>
            <div className="p-3 space-y-1 max-h-[calc(17*48px)] overflow-y-auto">
              {todayTasks.map(item => (
                item.task && (
                  <div key={item.taskId}
                    draggable={!item.task.done}
                    onDragStart={() => setDraggingTask({ id: item.task.id, title: item.task.title })}
                    onDragEnd={() => { setDraggingTask(null); setDropTarget(null) }}
                    className={`flex items-center gap-2 group ${!item.task.done ? 'cursor-grab active:cursor-grabbing' : ''}`}>
                    <input type="checkbox" checked={item.task.done} onChange={() => toggleTodayTask(item.task)} />
                    <span className={`text-sm flex-1 select-none ${item.task.done ? 'line-through text-[var(--text-muted)]' : ''}`}>
                      {item.task.title}
                    </span>
                    {!item.task.done && (
                      <span className="text-[var(--text-faint)] opacity-0 group-hover:opacity-100 text-xs">⠿</span>
                    )}
                  </div>
                )
              ))}
              {todayTasks.length === 0 && (
                <p className="text-xs text-[var(--text-muted)] py-2">No tasks for today</p>
              )}
            </div>
          </div>

          {/* Habits panel */}
          <div className="bg-[var(--bg-card)] rounded-xl border border-[var(--border)] overflow-hidden mt-3">
            <div className="px-4 py-2.5 border-b border-[var(--border)] bg-[var(--bg)]">
              <h3 className="font-semibold text-xs flex items-center justify-between">
                <span className="flex items-center gap-1.5">✅ Habits</span>
                <span className="text-[10px] text-[var(--text-muted)] font-normal">{selectedDate === new Date().toISOString().slice(0, 10) ? 'today' : selectedDate.slice(5)}</span>
              </h3>
            </div>
            <div className="p-3 space-y-2">
              {habits.map(h => {
                const val = habitEntries[h.id]
                return (
                  <div key={h.id} className="flex items-center justify-between">
                    <span className="text-sm truncate flex-1 mr-2">{h.name}</span>
                    <div className="flex gap-1 flex-shrink-0">
                      {(['YES', 'NO', 'SKIP'] as const).map(v => (
                        <button key={v} onClick={() => toggleHabit(h.id, v)}
                          className={`w-7 h-7 rounded-md text-[10px] font-medium transition-colors
                            ${val === v
                              ? v === 'YES' ? 'bg-[var(--accent-sage)] text-white'
                                : v === 'NO' ? 'bg-[var(--danger)] text-white'
                                : 'bg-[var(--border)] text-[var(--text)]'
                              : 'bg-[var(--bg-surface)] text-[var(--text-muted)] hover:bg-[var(--border-subtle)]'}`}>
                          {v === 'YES' ? '✓' : v === 'NO' ? '✗' : '–'}
                        </button>
                      ))}
                    </div>
                  </div>
                )
              })}
              {habits.length === 0 && (
                <p className="text-xs text-[var(--text-muted)] py-2">No habits tracked</p>
              )}
            </div>
          </div>

          {/* Daily notes panel — daily view only */}
          {view === 'daily' && <DailyNotePanel date={selectedDate} />}
        </div>
      </div>

      {/* Event detail modal for notes */}
      {editingEvent && (
        <div className="fixed inset-0 bg-black/30 z-50 flex items-center justify-center" onClick={() => saveNotes()}>
          <div className="bg-[var(--bg-card)] rounded-xl border border-[var(--border)] shadow-lg w-96 max-w-[90vw]" onClick={e => e.stopPropagation()}>
            <div className="px-4 py-3 border-b border-[var(--border)] flex items-center justify-between">
              <h3 className="font-semibold text-sm">{editingEvent.title}</h3>
              <span className="text-xs text-[var(--text-muted)]">
                {editingEvent.startTime}–{editingEvent.endTime}
              </span>
            </div>
            <div className="p-4">
              <label className="text-xs text-[var(--text-muted)] mb-1 block">Notes</label>
              <textarea
                value={editNotes}
                onChange={e => setEditNotes(e.target.value)}
                placeholder="Add notes..."
                autoFocus
                rows={4}
                className="w-full px-3 py-2 border border-[var(--border)] rounded-lg outline-none focus:border-[var(--accent-sage)] text-sm resize-none"
              />
            </div>
            <div className="px-4 py-3 border-t border-[var(--border)] flex justify-end gap-2">
              <button onClick={() => { setEditingEvent(null) }} className="px-3 py-1.5 text-sm text-[var(--text-muted)]">Cancel</button>
              <button onClick={saveNotes} className="px-4 py-1.5 bg-[var(--accent-sage)] text-white rounded-lg text-sm">Save</button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}
