import { useState, useEffect, useCallback, useRef } from 'react'
import { Editor } from '@tiptap/core'
import { Plugin, PluginKey } from '@tiptap/pm/state'

interface SlashItem {
  title: string
  icon: string
  description: string
  command: (editor: Editor) => void
}

const slashItems: SlashItem[] = [
  { title: 'Heading 1', icon: 'H1', description: 'Large heading', command: (e) => e.chain().focus().toggleHeading({ level: 1 }).run() },
  { title: 'Heading 2', icon: 'H2', description: 'Medium heading', command: (e) => e.chain().focus().toggleHeading({ level: 2 }).run() },
  { title: 'Heading 3', icon: 'H3', description: 'Small heading', command: (e) => e.chain().focus().toggleHeading({ level: 3 }).run() },
  { title: 'Bullet List', icon: '•', description: 'Unordered list', command: (e) => e.chain().focus().toggleBulletList().run() },
  { title: 'Numbered List', icon: '1.', description: 'Ordered list', command: (e) => e.chain().focus().toggleOrderedList().run() },
  { title: 'Task List', icon: '☑', description: 'Checklist', command: (e) => e.chain().focus().toggleTaskList().run() },
  { title: 'Code Block', icon: '</>', description: 'Code snippet', command: (e) => e.chain().focus().toggleCodeBlock().run() },
  { title: 'Blockquote', icon: '"', description: 'Quote block', command: (e) => e.chain().focus().toggleBlockquote().run() },
  { title: 'Table', icon: '⊞', description: '3×3 table', command: (e) => e.chain().focus().insertTable({ rows: 3, cols: 3 }).run() },
  { title: 'Divider', icon: '—', description: 'Horizontal rule', command: (e) => e.chain().focus().setHorizontalRule().run() },
]

const pluginKey = new PluginKey('slashMenu')

export function useSlashMenu(editor: Editor | null) {
  const [open, setOpen] = useState(false)
  const [query, setQuery] = useState('')
  const [pos, setPos] = useState({ top: 0, left: 0 })
  const [selected, setSelected] = useState(0)
  const slashPosRef = useRef<number | null>(null) // position where '/' was typed
  const containerRef = useRef<HTMLDivElement | null>(null)

  const filtered = slashItems.filter(i =>
    i.title.toLowerCase().includes(query.toLowerCase()) ||
    i.description.toLowerCase().includes(query.toLowerCase())
  )

  // Reset selection when filter changes
  useEffect(() => { setSelected(0) }, [query])

  const close = useCallback(() => {
    setOpen(false)
    setQuery('')
    slashPosRef.current = null
  }, [])

  const executeItem = useCallback((item: SlashItem) => {
    if (!editor || slashPosRef.current === null) return
    // Delete from the '/' to current cursor position
    const { from } = editor.state.selection
    editor.chain()
      .deleteRange({ from: slashPosRef.current, to: from })
      .run()
    item.command(editor)
    close()
  }, [editor, close])

  // Watch for '/' typed at start of line or after whitespace
  useEffect(() => {
    if (!editor) return

    const handleUpdate = () => {
      const { from } = editor.state.selection
      if (from < 1) { if (open) close(); return }

      // Get the text from the start of the current text block to cursor
      const $pos = editor.state.doc.resolve(from)
      const textBlockStart = $pos.start()
      const textBefore = editor.state.doc.textBetween(textBlockStart, from, '\n')

      // Match '/' at start of block or after whitespace, followed by optional query
      const match = textBefore.match(/(?:^|\s)\/([a-zA-Z0-9]*)$/)

      if (match) {
        const queryText = match[1]
        const slashPos = from - queryText.length - 1 // position of '/'

        setQuery(queryText)
        slashPosRef.current = slashPos

        if (!open) {
          // Calculate position
          try {
            const coords = editor.view.coordsAtPos(from)
            const editorDom = editor.view.dom.parentElement
            if (editorDom) {
              const rect = editorDom.getBoundingClientRect()
              setPos({
                top: coords.bottom - rect.top + 4,
                left: Math.min(coords.left - rect.left, rect.width - 260)
              })
            }
          } catch { /* ignore positioning errors */ }
          setOpen(true)
        }
      } else if (open) {
        close()
      }
    }

    editor.on('update', handleUpdate)
    editor.on('selectionUpdate', handleUpdate)
    return () => {
      editor.off('update', handleUpdate)
      editor.off('selectionUpdate', handleUpdate)
    }
  }, [editor, open, close])

  // Handle keyboard navigation — use capture phase to intercept before TipTap
  useEffect(() => {
    if (!open || !editor) return

    const handleKeyDown = (e: KeyboardEvent) => {
      if (e.key === 'ArrowDown') {
        e.preventDefault()
        e.stopPropagation()
        setSelected(s => filtered.length > 0 ? (s + 1) % filtered.length : 0)
      } else if (e.key === 'ArrowUp') {
        e.preventDefault()
        e.stopPropagation()
        setSelected(s => filtered.length > 0 ? (s - 1 + filtered.length) % filtered.length : 0)
      } else if (e.key === 'Enter') {
        e.preventDefault()
        e.stopPropagation()
        if (filtered[selected]) executeItem(filtered[selected])
        else close()
      } else if (e.key === 'Escape') {
        e.preventDefault()
        e.stopPropagation()
        close()
      }
    }

    // Use capture phase so we get the event before TipTap/ProseMirror
    document.addEventListener('keydown', handleKeyDown, true)
    return () => document.removeEventListener('keydown', handleKeyDown, true)
  }, [open, editor, filtered, selected, executeItem, close])

  // Close on click outside
  useEffect(() => {
    if (!open) return
    const handleClick = (e: MouseEvent) => {
      if (containerRef.current && !containerRef.current.contains(e.target as Node)) {
        close()
      }
    }
    // Delay to avoid closing immediately on the same click that might have triggered it
    const timer = setTimeout(() => document.addEventListener('mousedown', handleClick), 0)
    return () => { clearTimeout(timer); document.removeEventListener('mousedown', handleClick) }
  }, [open, close])

  return { open, pos, filtered, selected, select: executeItem, containerRef }
}
