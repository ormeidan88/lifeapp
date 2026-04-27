import { useEditor, EditorContent } from '@tiptap/react'
import StarterKit from '@tiptap/starter-kit'
import { Table } from '@tiptap/extension-table'
import { TableRow } from '@tiptap/extension-table-row'
import { TableCell } from '@tiptap/extension-table-cell'
import { TableHeader } from '@tiptap/extension-table-header'
import { Image } from '@tiptap/extension-image'
import { Link } from '@tiptap/extension-link'
import { TaskList } from '@tiptap/extension-task-list'
import { TaskItem } from '@tiptap/extension-task-item'
import { useEffect, useRef, useCallback } from 'react'
import { api } from '../../api/client'

export function DailyNotePanel({ date }: { date: string }) {
  const debounce = useRef<ReturnType<typeof setTimeout>>()
  const loading = useRef(false)
  const lastSavedJson = useRef<string>('')
  const currentDate = useRef(date)
  const latestContent = useRef<any>(null)

  const save = useCallback(async (d: string, content: any) => {
    const json = JSON.stringify(content)
    if (json === lastSavedJson.current) return
    lastSavedJson.current = json
    console.log('[DailyNote] SAVING to', d, json.substring(0, 100))
    try { await api.dailyNotes.put(d, { content: json }) }
    catch (e) { console.error('[DailyNote] save failed', e) }
  }, [])

  const editor = useEditor({
    extensions: [
      StarterKit.configure({ history: { depth: 100 } }),
      Table.configure({ resizable: true }),
      TableRow, TableCell, TableHeader,
      Image,
      Link.configure({ openOnClick: false }),
      TaskList,
      TaskItem.configure({ nested: true }),
    ],
    content: '',
    onUpdate: ({ editor }) => {
      if (loading.current) { console.log('[DailyNote] onUpdate skipped (loading)'); return }
      const content = editor.getJSON()
      latestContent.current = content
      clearTimeout(debounce.current)
      const d = currentDate.current
      console.log('[DailyNote] onUpdate, scheduling save for', d)
      debounce.current = setTimeout(() => save(d, content), 1000)
    },
  })

  useEffect(() => {
    if (!editor || editor.isDestroyed) return
    console.log('[DailyNote] date effect:', date, 'prev:', currentDate.current)

    if (debounce.current && currentDate.current !== date && latestContent.current) {
      clearTimeout(debounce.current)
      save(currentDate.current, latestContent.current)
    }
    currentDate.current = date
    latestContent.current = null

    let ignore = false
    loading.current = true
    ;(async () => {
      try {
        const data = await api.dailyNotes.get(date)
        console.log('[DailyNote] GET success:', date, data?.content?.substring(0, 80))
        if (ignore) return
        const parsed = data?.content ? JSON.parse(data.content) : ''
        editor.commands.setContent(parsed)
        lastSavedJson.current = data?.content || ''
      } catch (e) {
        console.log('[DailyNote] GET failed (404 = no note):', date, e)
        if (!ignore) {
          editor.commands.setContent('')
          lastSavedJson.current = ''
        }
      } finally {
        if (!ignore) loading.current = false
      }
    })()
    return () => { ignore = true }
  }, [date, editor, save])

  useEffect(() => {
    return () => {
      console.log('[DailyNote] UNMOUNT, latestContent:', !!latestContent.current)
      clearTimeout(debounce.current)
      if (latestContent.current) {
        save(currentDate.current, latestContent.current)
      }
    }
  }, [save])

  if (!editor) return null

  return (
    <div className="bg-[var(--bg-card)] rounded-xl border border-[var(--border)] overflow-hidden mt-3">
      <div className="px-4 py-2.5 border-b border-[var(--border)] bg-[var(--bg)]">
        <h3 className="font-semibold text-xs flex items-center justify-between">
          <span className="flex items-center gap-1.5">📝 Notes</span>
          <span className="text-[10px] text-[var(--text-muted)] font-normal">{date.slice(5)}</span>
        </h3>
      </div>
      <div className="daily-note-editor">
        <EditorContent editor={editor} />
      </div>
    </div>
  )
}
