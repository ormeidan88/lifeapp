import { Node, mergeAttributes } from '@tiptap/core'
import { ReactNodeViewRenderer, NodeViewWrapper } from '@tiptap/react'
import { useEffect, useState } from 'react'
import { api } from '../../api/client'

// React component that renders the page link
function PageLinkView({ node, editor }: any) {
  const [title, setTitle] = useState(node.attrs.title || 'Loading...')
  const pageId = node.attrs.pageId

  useEffect(() => {
    // Resolve title at render time from API
    if (pageId) {
      api.pages.get(pageId).then(p => {
        if (p?.title) setTitle(p.title)
      }).catch(() => setTitle('Unknown page'))
    }
  }, [pageId])

  const handleClick = () => {
    // Dispatch a custom event the app can listen to for navigation
    window.dispatchEvent(new CustomEvent('navigate-page', { detail: { pageId } }))
  }

  return (
    <NodeViewWrapper as="span" className="inline">
      <span
        onClick={handleClick}
        className="inline-flex items-center gap-1 px-1.5 py-0.5 bg-[var(--bg-surface)] rounded cursor-pointer hover:bg-[var(--border)] text-sm"
        contentEditable={false}
      >
        📄 {title}
      </span>
    </NodeViewWrapper>
  )
}

// TipTap extension definition
export const PageLink = Node.create({
  name: 'pageLink',
  group: 'inline',
  inline: true,
  atom: true,

  addAttributes() {
    return {
      pageId: { default: null },
      title: { default: 'Untitled' },
    }
  },

  parseHTML() {
    return [{ tag: 'span[data-page-link]' }]
  },

  renderHTML({ HTMLAttributes }) {
    return ['span', mergeAttributes(HTMLAttributes, { 'data-page-link': '' }), `📄 ${HTMLAttributes.title}`]
  },

  addNodeView() {
    return ReactNodeViewRenderer(PageLinkView)
  },
})
