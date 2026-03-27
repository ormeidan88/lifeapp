import { useState, useEffect, useRef } from 'react'
import { api } from '../api/client'
import { MiniEditor } from '../components/editor/MiniEditor'

export function MemorizePage() {
  const [decks, setDecks] = useState<any[]>([])
  const [selected, setSelected] = useState<string | null>(null)
  const [cards, setCards] = useState<any[]>([])
  const [allCards, setAllCards] = useState<any[]>([])
  const [flipped, setFlipped] = useState(false)
  const [cardIdx, setCardIdx] = useState(0)
  const [adding, setAdding] = useState(false)
  const [deckName, setDeckName] = useState('')
  const [frontContent, setFrontContent] = useState<any>(null)
  const [backContent, setBackContent] = useState<any>(null)
  const [mode, setMode] = useState<'review' | 'manage'>('review')
  const [loading, setLoading] = useState(true)

  useEffect(() => { loadDecks() }, [])

  const loadDecks = async () => { setLoading(true); const d = await api.decks.list(); setDecks(d.decks); setLoading(false) }

  const startReview = async (deckId: string) => {
    setSelected(deckId)
    const d = await api.decks.getReview(deckId)
    if (d.cards.length === 0) {
      setMode('manage')
      const all = await api.decks.getCards(deckId)
      setAllCards(all.cards)
      return
    }
    setCards(d.cards); setCardIdx(0); setFlipped(false); setMode('review')
  }

  const openManage = async (deckId: string) => {
    setSelected(deckId); setMode('manage')
    const all = await api.decks.getCards(deckId)
    setAllCards(all.cards)
  }

  const rate = async (rating: string) => {
    const card = cards[cardIdx]
    await api.decks.review(selected!, card.id, rating)
    if (cardIdx + 1 < cards.length) { setCardIdx(cardIdx + 1); setFlipped(false) }
    else { setSelected(null); loadDecks() }
  }

  const addDeck = async (e: React.FormEvent) => {
    e.preventDefault()
    if (!deckName.trim()) return
    await api.decks.create(deckName.trim())
    setDeckName(''); setAdding(false); loadDecks()
  }

  const addCard = async (e: React.FormEvent) => {
    e.preventDefault()
    if (!frontContent || !backContent) return
    await api.decks.createCard(selected!, JSON.stringify(frontContent), JSON.stringify(backContent))
    setFrontContent(null); setBackContent(null)
    const all = await api.decks.getCards(selected!)
    setAllCards(all.cards)
  }

  const deleteDeck = async (id: string) => {
    if (!confirm('Delete this deck and all its cards?')) return
    await api.decks.delete(id)
    loadDecks()
  }

  const goBack = () => { setSelected(null); setCards([]); setAllCards([]); loadDecks() }

  // Parse card content — handles both plain text (old cards) and JSON (new cards)
  const parseCardContent = (content: string) => {
    if (!content) return null
    try {
      const parsed = JSON.parse(content)
      if (parsed.type === 'doc') return parsed
      return null
    } catch {
      // Plain text — wrap in a doc
      return { type: 'doc', content: [{ type: 'paragraph', content: [{ type: 'text', text: content }] }] }
    }
  }

  // Get a plain text preview from card content
  const getCardPreview = (content: string) => {
    if (!content) return ''
    try {
      const parsed = JSON.parse(content)
      if (parsed.type === 'doc') {
        const extractText = (node: any): string => {
          if (node.text) return node.text
          if (node.content) return node.content.map(extractText).join(' ')
          return ''
        }
        return extractText(parsed).slice(0, 80) || '(rich content)'
      }
    } catch { /* plain text */ }
    return content.slice(0, 80)
  }

  // Review mode
  if (selected && mode === 'review' && cards.length > 0) {
    const card = cards[cardIdx]
    return (
      <div className="max-w-xl mx-auto text-center">
        <button onClick={goBack} className="text-[var(--text-muted)] hover:text-[var(--text)] text-sm mb-4 block">← Back</button>
        <p className="text-sm text-[var(--text-muted)] mb-4">{cardIdx + 1} / {cards.length}</p>
        {/* Progress bar */}
        <div className="w-full h-1 bg-gray-100 rounded-full mb-4 overflow-hidden">
          <div className="h-full bg-[var(--accent-sage)] rounded-full transition-all duration-300" style={{ width: `${((cardIdx + 1) / cards.length) * 100}%` }} />
        </div>
        <div className="bg-white p-8 rounded-2xl border border-[var(--border)] min-h-[220px] flex items-center justify-center cursor-pointer shadow-sm hover:shadow-md transition-shadow"
          onClick={() => setFlipped(!flipped)}>
          <div className="w-full" style={{ transition: 'opacity 0.15s', opacity: 1 }}>
            <MiniEditor content={parseCardContent(flipped ? card.back : card.front)} editable={false} minHeight="120px" />
          </div>
        </div>
        {flipped ? (
          <div className="flex gap-2 mt-5 justify-center">
            {[
              { key: 'AGAIN', label: 'Again', color: 'border-[var(--danger)] text-[var(--danger)] hover:bg-[var(--danger-light)]' },
              { key: 'HARD', label: 'Hard', color: 'border-[var(--accent-terracotta)] text-[var(--accent-terracotta)] hover:bg-orange-50' },
              { key: 'GOOD', label: 'Good', color: 'border-[var(--accent-sage)] text-[var(--accent-sage)] hover:bg-[var(--accent-sage-light)]' },
              { key: 'EASY', label: 'Easy', color: 'border-[var(--accent-blue)] text-[var(--accent-blue)] hover:bg-[var(--accent-blue-light)]' },
            ].map(r => (
              <button key={r.key} onClick={() => rate(r.key)}
                className={`px-5 py-2.5 rounded-xl border-2 text-sm font-medium transition-all active:scale-95 ${r.color}`}>{r.label}</button>
            ))}
          </div>
        ) : (
          <p className="text-sm text-[var(--text-muted)] mt-4">Click card to reveal answer</p>
        )}
      </div>
    )
  }

  // Manage mode
  if (selected && mode === 'manage') {
    const deckName = decks.find(d => d.id === selected)?.name || 'Deck'
    return (
      <div className="max-w-xl mx-auto">
        <button onClick={goBack} className="text-[var(--text-muted)] mb-4 hover:text-[var(--text)]">← Back</button>
        <h2 className="text-xl font-semibold mb-4">{deckName}</h2>
        {cards.length === 0 && mode === 'manage' && (
          <p className="text-sm text-[var(--accent-sage)] mb-4 bg-green-50 px-3 py-2 rounded">No cards due for review right now.</p>
        )}
        <h3 className="text-sm font-medium mb-2 text-[var(--text-muted)]">Add Card</h3>
        <form onSubmit={addCard} className="space-y-3 mb-6">
          <div>
            <label className="text-xs text-[var(--text-muted)] mb-1 block">Front</label>
            <MiniEditor content={frontContent} onChange={setFrontContent} placeholder="Question or prompt..." minHeight="60px" />
          </div>
          <div>
            <label className="text-xs text-[var(--text-muted)] mb-1 block">Back</label>
            <MiniEditor content={backContent} onChange={setBackContent} placeholder="Answer..." minHeight="60px" />
          </div>
          <button type="submit" className="px-4 py-2 bg-[var(--accent-sage)] text-white rounded-lg text-sm">Add Card</button>
        </form>
        {allCards.length > 0 && (
          <>
            <h3 className="text-sm font-medium mb-2 text-[var(--text-muted)]">All Cards ({allCards.length})</h3>
            <div className="space-y-2">
              {allCards.map(c => (
                <div key={c.id} className="bg-white rounded-lg border border-[var(--border)] overflow-hidden">
                  <div className="grid grid-cols-2 divide-x divide-[var(--border)]">
                    <div className="p-2 text-sm">
                      <div className="text-[10px] text-[var(--text-muted)] mb-1">Front</div>
                      <div className="text-xs">{getCardPreview(c.front)}</div>
                    </div>
                    <div className="p-2 text-sm">
                      <div className="text-[10px] text-[var(--text-muted)] mb-1">Back</div>
                      <div className="text-xs">{getCardPreview(c.back)}</div>
                    </div>
                  </div>
                </div>
              ))}
            </div>
          </>
        )}
      </div>
    )
  }

  // Deck list
  return (
    <div>
      <div className="flex items-center justify-between mb-6">
        <h1 className="text-2xl font-semibold">Memorize</h1>
        <button onClick={() => setAdding(true)} className="px-3 py-1.5 bg-[var(--accent-sage)] text-white rounded-lg text-sm hover:opacity-90">+ New Deck</button>
      </div>
      {adding && (
        <form onSubmit={addDeck} className="mb-4 flex gap-2">
          <input value={deckName} onChange={e => setDeckName(e.target.value)} placeholder="Deck name..." autoFocus
            onKeyDown={e => e.key === 'Escape' && (setAdding(false), setDeckName(''))}
            className="flex-1 px-3 py-2 border border-[var(--border)] rounded-lg outline-none focus:border-[var(--accent-sage)]" />
          <button type="submit" className="px-3 py-2 bg-[var(--accent-sage)] text-white rounded-lg text-sm">Create</button>
          <button type="button" onClick={() => { setAdding(false); setDeckName('') }} className="px-3 py-2 text-sm text-[var(--text-muted)]">Cancel</button>
        </form>
      )}
      {loading && decks.length === 0 && <p className="text-[var(--text-muted)] text-center py-12">Loading...</p>}
      <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
        {decks.map(d => (
          <div key={d.id} className="bg-white p-4 rounded-xl border border-[var(--border)] group">
            <div className="flex items-center justify-between mb-2">
              <h3 className="font-medium">{d.name}</h3>
              <button onClick={() => deleteDeck(d.id)}
                className="text-[var(--text-muted)] hover:text-[var(--danger)] text-xs opacity-0 group-hover:opacity-100">✕</button>
            </div>
            <p className="text-sm text-[var(--text-muted)] mb-3">{d.cardCount} cards · {d.dueCount} due</p>
            <div className="flex gap-2">
              <button onClick={() => startReview(d.id)}
                disabled={d.dueCount === 0}
                className={`px-3 py-1 rounded text-sm ${d.dueCount > 0 ? 'bg-[var(--accent-sage)] text-white' : 'bg-gray-100 text-[var(--text-muted)] cursor-not-allowed'}`}>
                {d.dueCount > 0 ? `Review (${d.dueCount})` : 'All caught up'}
              </button>
              <button onClick={() => openManage(d.id)} className="px-3 py-1 border border-[var(--border)] rounded text-sm">Manage</button>
            </div>
          </div>
        ))}
      </div>
      {!loading && decks.length === 0 && !adding && (
        <div className="text-center py-16">
          <div className="text-4xl mb-3">🧠</div>
          <p className="text-[var(--text-muted)]">No decks yet</p>
          <p className="text-xs text-[var(--text-muted)] mt-1">Create a deck to start memorizing</p>
        </div>
      )}
    </div>
  )
}
