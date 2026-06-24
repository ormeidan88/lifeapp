import { useState, useEffect } from 'react'

const DAY_LABELS = ['MON', 'TUE', 'WED', 'THU', 'FRI', 'SAT', 'SUN'] as const
const DAY_SHORT: Record<string, string> = { MON: 'M', TUE: 'T', WED: 'W', THU: 'T', FRI: 'F', SAT: 'S', SUN: 'S' }

function getDayOfWeek(dateStr: string): string {
  const d = new Date(dateStr + 'T00:00:00')
  return ['SUN', 'MON', 'TUE', 'WED', 'THU', 'FRI', 'SAT'][d.getDay()]
}

function getWeekdayLabel(dateStr: string): string {
  const d = new Date(dateStr + 'T00:00:00')
  const nth = Math.ceil(d.getDate() / 7)
  const ordinal = ['1st', '2nd', '3rd', '4th', '5th'][nth - 1]
  const weekday = d.toLocaleDateString('en', { weekday: 'long' })
  return `${ordinal} ${weekday}`
}

type RecurrenceRule = {
  freq: 'DAILY' | 'WEEKLY' | 'MONTHLY' | 'CUSTOM'
  interval?: number
  unit?: 'DAY' | 'WEEK' | 'MONTH'
  daysOfWeek?: string[]
  monthlyType?: 'DATE' | 'WEEKDAY'
  endDate?: string
}

type Props = {
  initial?: {
    title?: string; date?: string; startTime?: string; endTime?: string
    color?: string; notes?: string; recurrenceRule?: string
  }
  onSave: (data: any) => void
  onCancel: () => void
  mode: 'create' | 'edit'
}

export function EventFormModal({ initial, onSave, onCancel, mode }: Props) {
  const [title, setTitle] = useState(initial?.title || '')
  const [date, setDate] = useState(initial?.date || new Date().toISOString().slice(0, 10))
  const [startTime, setStartTime] = useState(initial?.startTime || '')
  const [endTime, setEndTime] = useState(initial?.endTime || '')
  const [color, setColor] = useState(initial?.color || '')
  const [notes, setNotes] = useState(initial?.notes || '')
  const [repeatType, setRepeatType] = useState<'none' | 'DAILY' | 'WEEKLY' | 'MONTHLY' | 'CUSTOM'>('none')
  const [weekDays, setWeekDays] = useState<string[]>([])
  const [monthlyType, setMonthlyType] = useState<'DATE' | 'WEEKDAY'>('DATE')
  const [customInterval, setCustomInterval] = useState(2)
  const [customUnit, setCustomUnit] = useState<'DAY' | 'WEEK' | 'MONTH'>('WEEK')
  const [endType, setEndType] = useState<'never' | 'date'>('never')
  const [endDate, setEndDate] = useState('')

  useEffect(() => {
    if (initial?.recurrenceRule) {
      try {
        const rule: RecurrenceRule = JSON.parse(initial.recurrenceRule)
        setRepeatType(rule.freq)
        if (rule.daysOfWeek) setWeekDays(rule.daysOfWeek)
        if (rule.monthlyType) setMonthlyType(rule.monthlyType)
        if (rule.freq === 'CUSTOM') {
          setCustomInterval(rule.interval || 2)
          setCustomUnit(rule.unit || 'WEEK')
        }
        if (rule.endDate) { setEndType('date'); setEndDate(rule.endDate) }
      } catch {}
    }
  }, [])

  useEffect(() => {
    if (repeatType === 'WEEKLY' && weekDays.length === 0 && date) {
      setWeekDays([getDayOfWeek(date)])
    }
  }, [repeatType, date])

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault()
    if (!title.trim() || !date) return
    let recurrenceRule: string | undefined
    if (repeatType !== 'none') {
      const rule: RecurrenceRule = { freq: repeatType }
      if (repeatType === 'WEEKLY') rule.daysOfWeek = weekDays.length > 0 ? weekDays : [getDayOfWeek(date)]
      if (repeatType === 'MONTHLY') rule.monthlyType = monthlyType
      if (repeatType === 'CUSTOM') { rule.interval = customInterval; rule.unit = customUnit }
      if (endType === 'date' && endDate) rule.endDate = endDate
      recurrenceRule = JSON.stringify(rule)
    }
    onSave({
      title: title.trim(), date, startTime: startTime || undefined,
      endTime: endTime || undefined, color: color || undefined,
      notes: notes || undefined, recurrenceRule,
    })
  }

  const toggleDay = (day: string) => {
    setWeekDays(prev => prev.includes(day) ? prev.filter(d => d !== day) : [...prev, day])
  }

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center" onClick={onCancel}>
      <div className="absolute inset-0 bg-black/20" />
      <form onSubmit={handleSubmit}
        className="relative bg-[var(--bg-card)] rounded-2xl border border-[var(--border)] w-96 max-w-[95vw] max-h-[85vh] overflow-y-auto"
        style={{ boxShadow: 'var(--shadow-float)' }}
        onClick={e => e.stopPropagation()}>
        <div className="p-4 space-y-3">
          {/* Title */}
          <input value={title} onChange={e => setTitle(e.target.value)} placeholder="Event title" autoFocus
            className="w-full px-3 py-2 border border-[var(--border)] rounded-lg outline-none focus:border-[var(--accent-sage)] text-sm" />

          {/* Date + Time */}
          <div className="flex gap-2">
            <input type="date" value={date} onChange={e => setDate(e.target.value)}
              className="flex-1 px-3 py-2 border border-[var(--border)] rounded-lg outline-none text-sm" />
            <input type="time" value={startTime} onChange={e => setStartTime(e.target.value)}
              className="w-24 px-2 py-2 border border-[var(--border)] rounded-lg outline-none text-sm" />
            <span className="self-center text-xs text-[var(--text-muted)]">–</span>
            <input type="time" value={endTime} onChange={e => setEndTime(e.target.value)}
              className="w-24 px-2 py-2 border border-[var(--border)] rounded-lg outline-none text-sm" />
          </div>

          {/* Color */}
          <div className="flex items-center gap-2">
            <span className="text-xs text-[var(--text-muted)]">Color</span>
            {['', '#7B9EB2', '#9BB07B', '#C4956A', '#B07BA5', '#6A9EC4'].map(c => (
              <button key={c} type="button" onClick={() => setColor(c)}
                className={`w-5 h-5 rounded-full transition-[box-shadow,transform] duration-150 ${color === c ? 'ring-2 ring-[var(--accent-sage)] ring-offset-1' : 'hover:scale-110'}`}
                style={{ backgroundColor: c || 'var(--bg-surface)', border: c ? 'none' : '1px solid var(--border)' }} />
            ))}
          </div>

          {/* Notes */}
          <textarea value={notes} onChange={e => setNotes(e.target.value)} placeholder="Notes" rows={2}
            className="w-full px-3 py-2 border border-[var(--border)] rounded-lg outline-none focus:border-[var(--accent-sage)] text-sm resize-none" />

          {/* Repeat */}
          <div>
            <label className="text-xs text-[var(--text-muted)] mb-1 block">Repeat</label>
            <select value={repeatType} onChange={e => setRepeatType(e.target.value as any)}
              className="w-full px-3 py-2 border border-[var(--border)] rounded-lg outline-none text-sm bg-[var(--bg-card)]">
              <option value="none">Does not repeat</option>
              <option value="DAILY">Daily</option>
              <option value="WEEKLY">Weekly</option>
              <option value="MONTHLY">Monthly</option>
              <option value="CUSTOM">Custom...</option>
            </select>

            {repeatType === 'WEEKLY' && (
              <div className="flex gap-1 mt-2">
                {DAY_LABELS.map(d => (
                  <button key={d} type="button" onClick={() => toggleDay(d)}
                    className={`w-7 h-7 rounded-full text-[10px] font-medium transition-colors
                      ${weekDays.includes(d) ? 'bg-[var(--accent-sage)] text-white' : 'bg-[var(--bg-surface)] text-[var(--text-muted)] hover:bg-[var(--border-subtle)]'}`}>
                    {DAY_SHORT[d]}
                  </button>
                ))}
              </div>
            )}

            {repeatType === 'MONTHLY' && date && (
              <div className="mt-2 space-y-1">
                <label className="flex items-center gap-2 text-xs cursor-pointer">
                  <input type="radio" name="monthlyType" checked={monthlyType === 'DATE'} onChange={() => setMonthlyType('DATE')} />
                  On day {new Date(date + 'T00:00:00').getDate()}
                </label>
                <label className="flex items-center gap-2 text-xs cursor-pointer">
                  <input type="radio" name="monthlyType" checked={monthlyType === 'WEEKDAY'} onChange={() => setMonthlyType('WEEKDAY')} />
                  On the {getWeekdayLabel(date)}
                </label>
              </div>
            )}

            {repeatType === 'CUSTOM' && (
              <div className="flex gap-2 mt-2 items-center">
                <span className="text-xs text-[var(--text-muted)]">Every</span>
                <input type="number" min={1} value={customInterval} onChange={e => setCustomInterval(parseInt(e.target.value) || 1)}
                  className="w-14 px-2 py-1.5 border border-[var(--border)] rounded-lg outline-none text-xs text-center" />
                <select value={customUnit} onChange={e => setCustomUnit(e.target.value as any)}
                  className="px-2 py-1.5 border border-[var(--border)] rounded-lg outline-none text-xs bg-[var(--bg-card)]">
                  <option value="DAY">days</option>
                  <option value="WEEK">weeks</option>
                  <option value="MONTH">months</option>
                </select>
              </div>
            )}

            {repeatType !== 'none' && (
              <div className="mt-2 space-y-1">
                <label className="flex items-center gap-2 text-xs cursor-pointer">
                  <input type="radio" name="endType" checked={endType === 'never'} onChange={() => setEndType('never')} />
                  <span className="text-[var(--text-muted)]">Never ends</span>
                </label>
                <label className="flex items-center gap-2 text-xs cursor-pointer">
                  <input type="radio" name="endType" checked={endType === 'date'} onChange={() => setEndType('date')} />
                  <span className="text-[var(--text-muted)]">Until</span>
                  {endType === 'date' && (
                    <input type="date" value={endDate} onChange={e => setEndDate(e.target.value)}
                      className="px-2 py-1 border border-[var(--border)] rounded-lg outline-none text-xs" />
                  )}
                </label>
              </div>
            )}
          </div>
        </div>

        {/* Actions */}
        <div className="px-4 pb-4 flex justify-end gap-2">
          <button type="button" onClick={onCancel} className="px-3 py-1.5 text-sm text-[var(--text-muted)]">Cancel</button>
          <button type="submit" className="px-3 py-1.5 bg-[var(--accent-sage)] text-white rounded-lg text-sm font-medium hover:opacity-90 transition-opacity">
            {mode === 'create' ? 'Add' : 'Save'}
          </button>
        </div>
      </form>
    </div>
  )
}
