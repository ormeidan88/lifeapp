import { useState, useEffect, useRef } from 'react'
import { api } from '../api/client'
import { PageEditor } from '../components/editor/PageEditor'

export function BooksPage() {
  const [books, setBooks] = useState<any[]>([])
  const [query, setQuery] = useState('')
  const [results, setResults] = useState<any[]>([])
  const [searching, setSearching] = useState(false)
  const [selected, setSelected] = useState<any>(null)
  const [page, setPage] = useState<any>(null)
  const [loading, setLoading] = useState(true)
  const [searchLoading, setSearchLoading] = useState(false)
  const debounceRef = useRef<ReturnType<typeof setTimeout>>()

  useEffect(() => { load() }, [])
  const load = async () => { setLoading(true); const d = await api.books.list(); setBooks(d.books); setLoading(false) }

  // Debounced search as you type
  const handleQueryChange = (value: string) => {
    setQuery(value)
    clearTimeout(debounceRef.current)
    if (!value.trim()) { setResults([]); setSearchLoading(false); return }
    setSearchLoading(true)
    debounceRef.current = setTimeout(async () => {
      try {
        const d = await api.books.search(value)
        setResults(d.results || [])
      } catch { setResults([]) }
      setSearchLoading(false)
    }, 400)
  }

  const addBook = async (book: any) => {
    try {
      await api.books.create(book)
      setSearching(false); setResults([]); setQuery(''); load()
    } catch { alert('This book has already been added.') }
  }

  const openBook = async (book: any) => {
    setSelected(book)
    if (book.pageId) { const p = await api.pages.get(book.pageId); setPage(p) }
  }

  const deleteBook = async (id: string) => {
    if (!confirm('Delete this book and its notes?')) return
    await api.books.delete(id)
    load()
  }

  const savePage = async (content: any) => { if (page?.id) await api.pages.update(page.id, { content }) }

  const openSearch = () => {
    setSearching(true)
    setQuery('')
    setResults([])
  }

  // Book detail view
  if (selected) {
    return (
      <div>
        <div className="flex items-center justify-between mb-4">
          <button onClick={() => { setSelected(null); setPage(null) }} className="text-[var(--text-muted)] hover:text-[var(--text)]">← Back</button>
          <button onClick={() => { deleteBook(selected.id); setSelected(null); setPage(null) }}
            className="text-xs text-[var(--danger)] hover:underline">Delete book</button>
        </div>
        <div className="flex gap-4 mb-6">
          {selected.coverUrl && <img src={selected.coverUrl} alt="" className="w-24 h-36 object-cover rounded-lg shadow-sm" />}
          <div>
            <h1 className="text-2xl font-semibold">{selected.title}</h1>
            <p className="text-[var(--text-muted)]">{selected.authors?.join(', ')}</p>
          </div>
        </div>
        {page && <PageEditor content={page.content} onUpdate={savePage} />}
      </div>
    )
  }

  // Main view
  return (
    <div>
      <div className="flex items-center justify-between mb-6">
        <h1 className="text-2xl font-semibold">Books</h1>
        <button onClick={() => searching ? (setSearching(false), setQuery(''), setResults([])) : openSearch()}
          className="px-3 py-1.5 bg-[var(--accent-sage)] text-white rounded-lg text-sm font-medium hover:opacity-90">
          {searching ? 'Cancel' : '+ Add Book'}
        </button>
      </div>

      {/* Search panel */}
      {searching && (
        <div className="mb-6 bg-white p-4 rounded-xl border border-[var(--border)]">
          <input value={query} onChange={e => handleQueryChange(e.target.value)}
            placeholder="Search by title or author..." autoFocus
            className="w-full px-3 py-2.5 border border-[var(--border)] rounded-lg outline-none text-sm mb-3" />

          {searchLoading && <p className="text-sm text-[var(--text-muted)] py-2">Searching...</p>}

          {!searchLoading && results.length > 0 && (
            <div className="space-y-2">
              {results.map(r => (
                <div key={r.googleBooksId} className="flex gap-3 p-3 rounded-lg border border-[var(--border)] items-center hover:bg-[var(--bg)] transition-colors">
                  {r.coverUrl
                    ? <img src={r.coverUrl} alt="" className="w-12 h-16 object-cover rounded flex-shrink-0" />
                    : <div className="w-12 h-16 bg-gray-100 rounded flex items-center justify-center text-lg flex-shrink-0">📖</div>
                  }
                  <div className="flex-1 min-w-0">
                    <p className="font-medium text-sm">{r.title}</p>
                    <p className="text-xs text-[var(--text-muted)]">{r.authors?.join(', ')}</p>
                    {r.description && <p className="text-xs text-[var(--text-muted)] mt-0.5 line-clamp-1">{r.description}</p>}
                  </div>
                  <button onClick={() => addBook(r)} className="px-3 py-1.5 bg-[var(--accent-sage)] text-white rounded-lg text-xs flex-shrink-0">Add</button>
                </div>
              ))}
            </div>
          )}

          {!searchLoading && results.length === 0 && query.trim().length > 0 && (
            <p className="text-sm text-[var(--text-muted)] text-center py-4">No results found</p>
          )}

          {!query.trim() && (
            <p className="text-sm text-[var(--text-muted)] text-center py-4">Start typing to search</p>
          )}
        </div>
      )}

      {/* Book library grid */}
      {loading && books.length === 0 && <p className="text-[var(--text-muted)] text-center py-12">Loading...</p>}
      <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-4 gap-4">
        {books.map(b => (
          <div key={b.id} className="cursor-pointer hover:shadow-sm transition-shadow group relative">
            <button onClick={(e) => { e.stopPropagation(); deleteBook(b.id) }}
              className="absolute top-2 right-2 text-[var(--text-muted)] hover:text-[var(--danger)] text-xs opacity-0 group-hover:opacity-100 bg-white rounded-full w-5 h-5 flex items-center justify-center shadow-sm">✕</button>
            <div onClick={() => openBook(b)}>
              {b.coverUrl
                ? <img src={b.coverUrl} alt="" className="w-full aspect-[2/3] object-cover rounded-lg mb-2 bg-gray-100" />
                : <div className="w-full aspect-[2/3] rounded-lg mb-2 bg-gray-100 flex items-center justify-center text-3xl">📖</div>
              }
              <p className="font-medium text-sm leading-snug">{b.title}</p>
              <p className="text-xs text-[var(--text-muted)] mt-0.5">{b.authors?.join(', ')}</p>
            </div>
          </div>
        ))}
      </div>
      {!loading && books.length === 0 && !searching && (
        <div className="text-center py-16">
          <div className="text-4xl mb-3">📚</div>
          <p className="text-[var(--text-muted)]">No books yet</p>
          <p className="text-xs text-[var(--text-muted)] mt-1">Add a book to start taking notes</p>
        </div>
      )}
    </div>
  )
}
