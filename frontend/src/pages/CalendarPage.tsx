import { useState, useEffect, useRef } from 'react'
import { api } from '../api/client'
import { DailyNotePanel } from '../components/calendar/DailyNotePanel'
import { EventFormModal } from '../components/calendar/EventFormModal'
import { RecurrencePrompt } from '../components/calendar/RecurrencePrompt'

const weekDays = ['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun']
const hours = Array.from({ length: 17 }, (_, i) => i + 6) // 6am-10pm

const getWeekDates = (offset: number) => {
  const d = new Date()
  const day = d.getDay()
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
  const [dragging, setDragging] = useState<string | null>(null)
  const [draggingEvent, setDraggingEvent] = useState<any | null>(null)
  const [draggingTask, setDraggingTask] = useState<{ id: string; title: string } | null>(null)
  const [dropTarget, setDropTarget] = useState<string | null>(null)

  // Modal state
  const [formModal, setFormModal] = useState<{ mode: 'create' | 'edit'; initial?: any; event?: any } | null>(null)
  const [recurrencePrompt, setRecurrencePrompt] = useState<{
    action: 'edit' | 'delete' | 'move'
    event: any
    pendingData?: any // for edit/move
  } | null>(null)

  const dragTarget = useRef<{ date: string; hour: number } | null>(null)

  const dates = view === 'weekly' ? getWeekDates(weekOffset) : [getDayDate(dayOffset)]
  const from = dates[0]
  const to = dates[dates.length - 1]
  const selectedDate = view === 'daily' ? getDayDate(dayOffset) : new Date().toISOString().slice(0, 10)

  useEffect(() => {
    let ignore = false
    ;(async () => {
      try {
        const d = await api.calendar.list(from, to)
        if (!ignore) setEvents(d.events || [])
      } catch { if (!ignore) setEvents([]) }
    })()
    return () => { ignore = true }
  }, [weekOffset, dayOffset, view])

  useEffect(() => {
    let ignore = false
    ;(async () => {
      try {
        const lists = await api.lists.list()
        if (ignore) return
        const today = lists.lists?.find((l: any) => l.name === 'Today')
        if (today) {
          setTodayListId(today.id)
          const items = await api.lists.getItems(today.id)
          if (ignore) return
          setTodayTasks(items.items || [])
        }
        const d = await api.habits.list()
        if (ignore) return
        setHabits(d.habits || [])
        const ent: Record<string, string> = {}
        for (const h of d.habits || []) {
          if (ignore) break
          try {
            const e = await api.habits.getEntries(h.id, selectedDate, selectedDate)
            if (!ignore && e.entries?.length > 0) ent[h.id] = e.entries[0].value
          } catch {}
        }
        if (!ignore) setHabitEntries(ent)
      } catch { if (!ignore) { setTodayTasks([]); setHabits([]); setHabitEntries({}) } }
    })()
    return () => { ignore = true }
  }, [selectedDate])

  const reloadEvents = async () => {
    try { const d = await api.calendar.list(from, to); setEvents(d.events || []) } catch { setEvents([]) }
  }

  const reloadTasks = async () => {
    try {
      const lists = await api.lists.list()
      const today = lists.lists?.find((l: any) => l.name === 'Today')
      if (today) { setTodayListId(today.id); const items = await api.lists.getItems(today.id); setTodayTasks(items.items || []) }
    } catch { setTodayTasks([]) }
  }

  const toggleTodayTask = async (task: any) => {
    await api.tasks.update(task.id, { projectId: task.projectId, done: !task.done })
    reloadTasks()
  }

  const toggleHabit = async (habitId: string, value: string) => {
    const current = habitEntries[habitId]
    const newVal = current === value ? '' : value
    if (newVal) {
      await api.habits.createEntry(habitId, selectedDate, newVal)
      setHabitEntries(prev => ({ ...prev, [habitId]: newVal }))
    }
  }

  // ── Event CRUD ──────────────────────────────────────────────────────

  const handleCreateEvent = async (data: any) => {
    await api.calendar.create(data)
    setFormModal(null)
    reloadEvents()
  }

  const handleEditEvent = (ev: any) => {
    if (ev.isRecurring) {
      setRecurrencePrompt({ action: 'edit', event: ev })
    } else {
      setFormModal({
        mode: 'edit', event: ev,
        initial: { title: ev.title, date: ev.date, startTime: ev.startTime, endTime: ev.endTime, color: ev.color, notes: ev.notes, recurrenceRule: ev.recurrenceRule },
      })
    }
  }

  const handleSaveEdit = async (data: any) => {
    const ev = formModal?.event
    if (!ev) return
    const body: any = { ...data }
    if (ev.isRecurring && ev._editMode) {
      body.editMode = ev._editMode
      body.occurrenceDate = ev.date
    }
    await api.calendar.update(ev.isRecurring ? (ev.seriesId || ev.id) : ev.id, body)
    setFormModal(null)
    reloadEvents()
  }

  const handleDeleteEvent = (ev: any, e: React.MouseEvent) => {
    e.stopPropagation()
    if (ev.isRecurring) {
      setRecurrencePrompt({ action: 'delete', event: ev })
    } else {
      doDelete(ev.id)
    }
  }

  const doDelete = async (id: string, deleteMode?: string, occurrenceDate?: string) => {
    await api.calendar.delete(id, deleteMode ? { deleteMode, occurrenceDate } : undefined)
    reloadEvents()
  }

  const handleRecurrenceChoice = async (mode: 'single' | 'all') => {
    const { action, event, pendingData } = recurrencePrompt!
    setRecurrencePrompt(null)

    if (action === 'delete') {
      await doDelete(event.seriesId || event.id, mode, event.date)
    } else if (action === 'edit') {
      // Open form with editMode attached
      setFormModal({
        mode: 'edit',
        event: { ...event, _editMode: mode },
        initial: {
          title: event.title, date: event.date, startTime: event.startTime,
          endTime: event.endTime, color: event.color, notes: event.notes,
          recurrenceRule: mode === 'all' ? event.recurrenceRule : undefined,
        },
      })
    } else if (action === 'move' && pendingData) {
      const { date, startTime } = pendingData
      await api.calendar.update(event.seriesId || event.id, {
        editMode: mode, occurrenceDate: event.date,
        date, startTime,
      })
      reloadEvents()
    }
  }

  // ── Drag & Drop ─────────────────────────────────────────────────────

  const handleDrop = async (date: string, hour: number) => {
    setDropTarget(null)
    const startTime = `${String(hour).padStart(2, '0')}:00`
    if (draggingTask) {
      const endTime = `${String(hour + 1).padStart(2, '0')}:00`
      await api.calendar.create({ title: draggingTask.title, date, startTime, endTime })
      setDraggingTask(null)
      reloadEvents()
    } else if (draggingEvent) {
      if (draggingEvent.isRecurring) {
        setRecurrencePrompt({ action: 'move', event: draggingEvent, pendingData: { date, startTime } })
      } else {
        await api.calendar.update(draggingEvent.id, { date, startTime })
        reloadEvents()
      }
      setDraggingEvent(null)
      setDragging(null)
    }
  }

  // ── Helpers ─────────────────────────────────────────────────────────

  const eventsForDateHour = (date: string, hour: number) =>
    events.filter(e => e.date === date && e.startTime && parseInt(e.startTime) === hour)

  // Compute overlap layout for all events on a given date
  const overlapLayout = (date: string): Map<string, { col: number; totalCols: number }> => {
    const dayEvents = events
      .filter(e => e.date === date && e.startTime)
      .map(e => {
        const sh = parseInt(e.startTime); const sm = parseInt(e.startTime?.split(':')[1] || '0')
        const eh = e.endTime ? parseInt(e.endTime) : sh + 1; const em = parseInt(e.endTime?.split(':')[1] || '0')
        return { id: e.id + '-' + e.date, start: sh * 60 + sm, end: eh * 60 + em }
      })
      .sort((a, b) => a.start - b.start || a.end - b.end)

    const layout = new Map<string, { col: number; totalCols: number }>()
    const groups: typeof dayEvents[] = []

    for (const ev of dayEvents) {
      let placed = false
      for (const group of groups) {
        if (group.some(g => g.start < ev.end && ev.start < g.end)) {
          group.push(ev); placed = true; break
        }
      }
      if (!placed) groups.push([ev])
    }

    // Merge overlapping groups that share transitive overlaps
    let merged = true
    while (merged) {
      merged = false
      for (let i = 0; i < groups.length; i++) {
        for (let j = i + 1; j < groups.length; j++) {
          if (groups[i].some(a => groups[j].some(b => a.start < b.end && b.start < a.end))) {
            groups[i].push(...groups[j]); groups.splice(j, 1); merged = true; break
          }
        }
        if (merged) break
      }
    }

    for (const group of groups) {
      // Assign columns greedily
      const cols: typeof dayEvents[] = []
      const sorted = [...group].sort((a, b) => a.start - b.start || a.end - b.end)
      for (const ev of sorted) {
        let placed = false
        for (let c = 0; c < cols.length; c++) {
          if (cols[c].every(g => g.end <= ev.start || ev.end <= g.start)) {
            cols[c].push(ev); layout.set(ev.id, { col: c, totalCols: 0 }); placed = true; break
          }
        }
        if (!placed) { layout.set(ev.id, { col: cols.length, totalCols: 0 }); cols.push([ev]) }
      }
      for (const ev of group) {
        const l = layout.get(ev.id)!; l.totalCols = cols.length
      }
    }
    return layout
  }

  // Cache per render
  const layoutCache = useRef<Map<string, Map<string, { col: number; totalCols: number }>>>(new Map())
  const getLayout = (date: string) => {
    if (!layoutCache.current.has(date)) layoutCache.current.set(date, overlapLayout(date))
    return layoutCache.current.get(date)!
  }
  // Clear cache when events change
  useEffect(() => { layoutCache.current = new Map() }, [events])

  const getEventStyle = (ev: any) => {
    const startH = parseInt(ev.startTime)
    const startM = parseInt(ev.startTime?.split(':')[1] || '0')
    const endH = ev.endTime ? parseInt(ev.endTime) : startH + 1
    const endM = parseInt(ev.endTime?.split(':')[1] || '0')
    const durationMin = (endH - startH) * 60 + (endM - startM)
    const layout = getLayout(ev.date)
    const info = layout.get(ev.id + '-' + ev.date)
    const col = info?.col ?? 0
    const totalCols = info?.totalCols ?? 1
    const widthPct = 100 / totalCols
    const leftPct = col * widthPct
    return {
      top: `${(startM / 60) * 48}px`,
      height: `${Math.max((durationMin / 60) * 48, 20)}px`,
      left: `calc(${leftPct}% + 1px)`,
      width: `calc(${widthPct}% - 2px)`,
      position: 'absolute' as const, zIndex: 5,
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
          <button onClick={() => setFormModal({ mode: 'create' })}
            className="px-3 py-1.5 bg-[var(--accent-sage)] text-white rounded-lg text-sm font-medium hover:opacity-90 transition-opacity">+ Event</button>
          <div className="flex border border-[var(--border)] rounded overflow-hidden text-sm">
            <button onClick={() => setView('daily')} className={`px-3 py-1 ${view === 'daily' ? 'bg-[var(--accent-sage)] text-white' : ''}`}>Day</button>
            <button onClick={() => setView('weekly')} className={`px-3 py-1 ${view === 'weekly' ? 'bg-[var(--accent-sage)] text-white' : ''}`}>Week</button>
          </div>
          <button onClick={nav.prev} className="px-3 py-1 border border-[var(--border)] rounded text-sm">←</button>
          <button onClick={nav.today} className="px-3 py-1 border border-[var(--border)] rounded text-sm">Today</button>
          <button onClick={nav.next} className="px-3 py-1 border border-[var(--border)] rounded text-sm">→</button>
        </div>
      </div>

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
                  onClick={() => setFormModal({ mode: 'create', initial: { date: d, startTime: `${String(h).padStart(2, '0')}:00`, endTime: `${String(h + 1).padStart(2, '0')}:00` } })}
                  onDragOver={e => { e.preventDefault(); dragTarget.current = { date: d, hour: h }; setDropTarget(`${d}-${h}`) }}
                  onDragLeave={() => setDropTarget(null)}
                  onDrop={() => handleDrop(d, h)}
                  className={`border-t border-l border-[var(--border)] h-[48px] cursor-pointer transition-colors
                    ${dropTarget === `${d}-${h}` && (draggingTask || draggingEvent) ? 'bg-[var(--accent-sage-light)] ring-1 ring-inset ring-[var(--accent-sage)]' : d === todayStr ? 'bg-[var(--accent-sage-light)]' : 'hover:bg-[var(--bg-surface)]'}`}>
                </div>
              ))}
            </div>
          ))}
        </div>
        {/* Event overlays — one per day column, positioned over the grid */}
        <div className={`grid ${view === 'weekly' ? 'min-w-[700px]' : 'min-w-[300px]'}`}
          style={{ gridTemplateColumns: `60px repeat(${dates.length}, 1fr)`, marginTop: `${-(hours.length * 48)}px`, pointerEvents: 'none' }}>
          <div />
          {dates.map(d => {
            const dayEvents = events.filter(e => e.date === d && e.startTime)
            return (
              <div key={`overlay-${d}`} className="relative" style={{ height: `${hours.length * 48}px` }}>
                {dayEvents.map(ev => {
                  const startH = parseInt(ev.startTime)
                  const startM = parseInt(ev.startTime?.split(':')[1] || '0')
                  const endH = ev.endTime ? parseInt(ev.endTime) : startH + 1
                  const endM = parseInt(ev.endTime?.split(':')[1] || '0')
                  const topMin = (startH - hours[0]) * 60 + startM
                  const durationMin = (endH - startH) * 60 + (endM - startM)
                  const layout = getLayout(d)
                  const info = layout.get(ev.id + '-' + ev.date)
                  const col = info?.col ?? 0
                  const totalCols = info?.totalCols ?? 1
                  const widthPct = 100 / totalCols
                  const leftPct = col * widthPct
                  return (
                    <div key={ev.id + '-' + ev.date}
                      draggable
                      onDragStart={(e) => { e.stopPropagation(); setDragging(ev.id); setDraggingEvent(ev) }}
                      onDragEnd={() => { setDragging(null); setDraggingEvent(null) }}
                      onClick={(e) => { e.stopPropagation(); handleEditEvent(ev) }}
                      style={{
                        position: 'absolute',
                        top: `${(topMin / 60) * 48}px`,
                        height: `${Math.max((durationMin / 60) * 48, 20)}px`,
                        left: `calc(${leftPct}% + 1px)`,
                        width: `calc(${widthPct}% - 2px)`,
                        zIndex: 5, pointerEvents: 'auto',
                        ...(ev.color ? { backgroundColor: ev.color + '33', color: ev.color } : {}),
                      }}
                      className={`text-xs px-1.5 py-0.5 rounded cursor-grab active:cursor-grabbing group/ev overflow-hidden ${!ev.color ? 'bg-[var(--accent-blue-light)] text-[var(--accent-blue)]' : ''}`}>
                      <span className="truncate block">
                        {ev.isRecurring && <span className="mr-0.5">🔁</span>}
                        {ev.title}
                      </span>
                      {ev.notes && <span className="ml-1 opacity-50">✎</span>}
                      <button onClick={(e) => handleDeleteEvent(ev, e)}
                        className="absolute -top-1 -right-1 w-4 h-4 bg-[var(--bg-card)] border border-[var(--border)] rounded-full text-[8px] text-[var(--danger)] opacity-0 group-hover/ev:opacity-100 flex items-center justify-center"
                        style={{ pointerEvents: 'auto' }}>✕</button>
                    </div>
                  )
                })}
                {/* Current time indicator */}
                {d === todayStr && currentHour >= hours[0] && currentHour <= hours[hours.length - 1] && (
                  <div className="absolute left-0 right-0 border-t-2 border-[var(--accent-terracotta)] z-10 pointer-events-none"
                    style={{ top: `${((currentHour - hours[0]) * 60 + currentMinute) / 60 * 48}px` }}>
                    <div className="w-2 h-2 rounded-full bg-[var(--accent-terracotta)] -mt-1 -ml-1" />
                  </div>
                )}
              </div>
            )
          })}
        </div>
        </div>

        {/* Side panels */}
        <div className={`${view === 'daily' ? 'w-80' : 'w-56'} flex-shrink-0 hidden lg:block`}>
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
                    {!item.task.done && <span className="text-[var(--text-faint)] opacity-0 group-hover:opacity-100 text-xs">⠿</span>}
                  </div>
                )
              ))}
              {todayTasks.length === 0 && <p className="text-xs text-[var(--text-muted)] py-2">No tasks for today</p>}
            </div>
          </div>

          <div className="bg-[var(--bg-card)] rounded-xl border border-[var(--border)] overflow-hidden mt-3">
            <div className="px-4 py-2.5 border-b border-[var(--border)] bg-[var(--bg)]">
              <h3 className="font-semibold text-xs flex items-center justify-between">
                <span className="flex items-center gap-1.5">✅ Habits</span>
                <span className="text-[10px] text-[var(--text-muted)] font-normal">{selectedDate === todayStr ? 'today' : selectedDate.slice(5)}</span>
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
              {habits.length === 0 && <p className="text-xs text-[var(--text-muted)] py-2">No habits tracked</p>}
            </div>
          </div>

          {view === 'daily' && <DailyNotePanel date={selectedDate} />}
        </div>
      </div>

      {/* Event form modal */}
      {formModal && (
        <EventFormModal
          mode={formModal.mode}
          initial={formModal.initial}
          onSave={formModal.mode === 'create' ? handleCreateEvent : handleSaveEdit}
          onCancel={() => setFormModal(null)}
        />
      )}

      {/* Recurrence prompt */}
      {recurrencePrompt && (
        <RecurrencePrompt
          action={recurrencePrompt.action}
          onChoice={handleRecurrenceChoice}
          onCancel={() => setRecurrencePrompt(null)}
        />
      )}
    </div>
  )
}
