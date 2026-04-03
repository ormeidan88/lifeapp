import { useEditor, EditorContent } from '@tiptap/react'
import StarterKit from '@tiptap/starter-kit'
import { Image } from '@tiptap/extension-image'
import { Link } from '@tiptap/extension-link'
import { Table } from '@tiptap/extension-table'
import { TableRow } from '@tiptap/extension-table-row'
import { TableCell } from '@tiptap/extension-table-cell'
import { TableHeader } from '@tiptap/extension-table-header'
import { TaskList } from '@tiptap/extension-task-list'
import { TaskItem } from '@tiptap/extension-task-item'

type Props = {
  content: any
  editable?: boolean
  onChange?: (json: any) => void
  placeholder?: string
  minHeight?: string
}

export function MiniEditor({ content, editable = true, onChange, placeholder, minHeight = '80px' }: Props) {
  const editor = useEditor({
    extensions: [
      StarterKit.configure({ history: { depth: 50 } }),
      Image,
      Link.configure({ openOnClick: false }),
      Table.configure({ resizable: false }),
      TableRow, TableCell, TableHeader,
      TaskList,
      TaskItem.configure({ nested: true }),
    ],
    content: content || undefined,
    editable,
    onUpdate: ({ editor }) => {
      onChange?.(editor.getJSON())
    },
  })

  if (!editor) return null

  return (
    <div className={`bg-[var(--bg-card)] rounded-lg border border-[var(--border)] ${editable ? '' : 'border-none'}`}>
      {editable && (
        <div className="flex gap-0.5 p-1 border-b border-[var(--border)] flex-wrap">
          {(['bold', 'italic'] as const).map(mark => (
            <button key={mark} onClick={() => editor.chain().focus().toggleMark(mark).run()} type="button"
              className={`w-7 h-7 rounded flex items-center justify-center text-xs transition-colors
                ${editor.isActive(mark) ? 'bg-[var(--accent-sage)] text-white' : 'hover:bg-[var(--bg)]'}`}>
              {mark === 'bold' ? 'B' : <em>I</em>}
            </button>
          ))}
          <button onClick={() => editor.chain().focus().toggleBulletList().run()} type="button"
            className={`w-7 h-7 rounded flex items-center justify-center text-xs ${editor.isActive('bulletList') ? 'bg-[var(--accent-sage)] text-white' : 'hover:bg-[var(--bg)]'}`}>
            •≡
          </button>
          <button onClick={() => editor.chain().focus().toggleCodeBlock().run()} type="button"
            className={`w-7 h-7 rounded flex items-center justify-center text-[10px] font-mono ${editor.isActive('codeBlock') ? 'bg-[var(--accent-sage)] text-white' : 'hover:bg-[var(--bg)]'}`}>
            {'</>'}
          </button>
        </div>
      )}
      <div style={{ minHeight }}>
        <EditorContent editor={editor} />
      </div>
    </div>
  )
}
