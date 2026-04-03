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
import { PageLink } from './PageLinkExtension'
import { useSlashMenu } from './SlashMenu'
import { PageLinkPicker } from './PageLinkPicker'
import { useEffect, useRef, useState } from 'react'
import { api } from '../../api/client'

type Props = {
  content: any
  onUpdate: (content: any) => void
}

export function PageEditor({ content, onUpdate }: Props) {
  const debounce = useRef<ReturnType<typeof setTimeout>>()
  const fileInput = useRef<HTMLInputElement>(null)

  const editor = useEditor({
    extensions: [
      StarterKit.configure({ history: { depth: 100 } }),
      Table.configure({ resizable: true }),
      TableRow, TableCell, TableHeader,
      Image,
      Link.configure({ openOnClick: false }),
      TaskList,
      TaskItem.configure({ nested: true }),
      PageLink,
    ],
    content,
    onUpdate: ({ editor }) => {
      clearTimeout(debounce.current)
      debounce.current = setTimeout(() => {
        onUpdate(editor.getJSON())
      }, 1500)
    },
  })

  const slash = useSlashMenu(editor)
  const [showPagePicker, setShowPagePicker] = useState(false)

  // Only set content on initial mount — after that, editor owns the state
  const contentSet = useRef(false)
  useEffect(() => {
    if (editor && content && !editor.isDestroyed && !contentSet.current) {
      editor.commands.setContent(content)
      contentSet.current = true
    }
  }, [editor, content])

  const handleImageUpload = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0]
    if (!file || !editor) return
    try {
      const result = await api.images.upload(file)
      if (result.url) editor.chain().focus().setImage({ src: result.url }).run()
    } catch (err) { console.error('Image upload failed', err) }
    e.target.value = ''
  }

  const insertPageLink = (pageId: string, title: string) => {
    if (!editor) return
    editor.chain().focus().insertContent({ type: 'pageLink', attrs: { pageId, title } }).run()
    setShowPagePicker(false)
  }

  if (!editor) return null

  return (
    <div className="rounded-xl border border-[var(--border)] relative overflow-hidden" style={{ background: 'var(--bg-card)', boxShadow: 'var(--shadow-sm)' }}>
      <input ref={fileInput} type="file" accept="image/jpeg,image/png,image/gif,image/webp" className="hidden" onChange={handleImageUpload} />
      <div className="editor-toolbar flex gap-0.5 p-1.5 flex-wrap items-center">
        {/* Text formatting */}
        <div className="flex gap-0.5 pr-2 mr-2 border-r border-[var(--border)]">
          {(['bold', 'italic'] as const).map(mark => (
            <button key={mark} onClick={() => editor.chain().focus().toggleMark(mark).run()}
              className={`w-8 h-8 rounded flex items-center justify-center text-sm font-medium transition-colors
                ${editor.isActive(mark) ? 'bg-[var(--accent-sage)] text-white' : 'hover:bg-[var(--bg)] text-[var(--text)]'}`}>
              {mark === 'bold' ? 'B' : <em>I</em>}
            </button>
          ))}
        </div>
        {/* Headings */}
        <div className="flex gap-0.5 pr-2 mr-2 border-r border-[var(--border)]">
          {[1, 2, 3].map(level => (
            <button key={level} onClick={() => editor.chain().focus().toggleHeading({ level: level as 1|2|3 }).run()}
              className={`w-8 h-8 rounded flex items-center justify-center text-xs font-medium transition-colors
                ${editor.isActive('heading', { level }) ? 'bg-[var(--accent-sage)] text-white' : 'hover:bg-[var(--bg)] text-[var(--text)]'}`}>
              H{level}
            </button>
          ))}
        </div>
        {/* Lists */}
        <div className="flex gap-0.5 pr-2 mr-2 border-r border-[var(--border)]">
          <button onClick={() => editor.chain().focus().toggleBulletList().run()} title="Bullet list"
            className={`w-8 h-8 rounded flex items-center justify-center text-sm transition-colors
              ${editor.isActive('bulletList') ? 'bg-[var(--accent-sage)] text-white' : 'hover:bg-[var(--bg)]'}`}>
            •≡
          </button>
          <button onClick={() => editor.chain().focus().toggleOrderedList().run()} title="Numbered list"
            className={`w-8 h-8 rounded flex items-center justify-center text-xs transition-colors
              ${editor.isActive('orderedList') ? 'bg-[var(--accent-sage)] text-white' : 'hover:bg-[var(--bg)]'}`}>
            1.
          </button>
          <button onClick={() => editor.chain().focus().toggleTaskList().run()} title="Task list"
            className={`w-8 h-8 rounded flex items-center justify-center text-sm transition-colors
              ${editor.isActive('taskList') ? 'bg-[var(--accent-sage)] text-white' : 'hover:bg-[var(--bg)]'}`}>
            ☑
          </button>
        </div>
        {/* Blocks */}
        <div className="flex gap-0.5 pr-2 mr-2 border-r border-[var(--border)]">
          <button onClick={() => editor.chain().focus().toggleCodeBlock().run()} title="Code block"
            className={`w-8 h-8 rounded flex items-center justify-center text-xs font-mono transition-colors
              ${editor.isActive('codeBlock') ? 'bg-[var(--accent-sage)] text-white' : 'hover:bg-[var(--bg)]'}`}>
            {'</>'}
          </button>
          <button onClick={() => editor.chain().focus().insertTable({ rows: 3, cols: 3 }).run()} title="Table"
            className="w-8 h-8 rounded flex items-center justify-center text-sm hover:bg-[var(--bg)] transition-colors">
            ⊞
          </button>
          <button onClick={() => editor.chain().focus().toggleBlockquote().run()} title="Quote"
            className={`w-8 h-8 rounded flex items-center justify-center text-sm transition-colors
              ${editor.isActive('blockquote') ? 'bg-[var(--accent-sage)] text-white' : 'hover:bg-[var(--bg)]'}`}>
            "
          </button>
        </div>
        {/* Insert */}
        <div className="flex gap-0.5">
          <button onClick={() => fileInput.current?.click()} title="Insert image"
            className="w-8 h-8 rounded flex items-center justify-center text-sm hover:bg-[var(--bg)] transition-colors">
            🖼
          </button>
          <button onClick={() => { const url = prompt('Enter link URL:'); if (url) editor.chain().focus().setLink({ href: url }).run() }}
            title="Insert link"
            className={`w-8 h-8 rounded flex items-center justify-center text-sm transition-colors
              ${editor.isActive('link') ? 'bg-[var(--accent-sage)] text-white' : 'hover:bg-[var(--bg)]'}`}>
            🔗
          </button>
          <button onClick={() => setShowPagePicker(!showPagePicker)} title="Link to page"
            className="w-8 h-8 rounded flex items-center justify-center text-sm hover:bg-[var(--bg)] transition-colors">
            📄
          </button>
        </div>
      </div>
      <EditorContent editor={editor} />

      {/* Slash command menu */}
      {slash.open && slash.filtered.length > 0 && (
        <div ref={slash.containerRef}
          className="absolute z-50 bg-[var(--bg-card)] border border-[var(--border)] rounded-xl py-1.5 w-60 max-h-72 overflow-y-auto"
          style={{ boxShadow: 'var(--shadow-float)' }}
          style={{ top: slash.pos.top, left: slash.pos.left }}>
          {slash.filtered.map((item, i) => (
            <button key={item.title}
              onClick={(e) => { e.preventDefault(); e.stopPropagation(); slash.select(item) }}
              onMouseEnter={() => {}}
              className={`w-full text-left px-3 py-2 flex items-center gap-3 transition-colors
                ${i === slash.selected ? 'bg-[var(--bg)]' : 'hover:bg-[var(--bg)]'}`}>
              <span className="w-8 h-8 rounded-lg bg-[var(--bg-surface)] flex items-center justify-center text-xs font-mono text-[var(--text-muted)] flex-shrink-0">{item.icon}</span>
              <div>
                <div className="text-sm font-medium">{item.title}</div>
                <div className="text-[10px] text-[var(--text-muted)]">{item.description}</div>
              </div>
            </button>
          ))}
        </div>
      )}
      {/* Page link picker */}
      {showPagePicker && (
        <div style={{ position: 'absolute', top: 50, right: 16, zIndex: 60 }}>
          <PageLinkPicker onSelect={insertPageLink} onClose={() => setShowPagePicker(false)} />
        </div>
      )}
    </div>
  )
}
