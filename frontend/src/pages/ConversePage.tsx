import { useState, useEffect, useRef } from 'react'
import ReactMarkdown from 'react-markdown'
import remarkGfm from 'remark-gfm'
import { api } from '../api/client'

type Message = { role: 'user' | 'assistant'; content: string }
type Persona = { id: string; name: string; systemPrompt: string; createdAt: string }
type Model = { id: string; name: string }

async function streamMessage(
  payload: object,
  onToken: (token: string) => void,
  onError: (msg: string) => void,
  onDone: () => void
) {
  try {
    const res = await fetch('/api/converse/message', {
      method: 'POST',
      credentials: 'include',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload),
    })

    if (!res.ok) {
      onError('Failed to connect to AI service')
      onDone()
      return
    }

    const reader = res.body!.getReader()
    const decoder = new TextDecoder()
    let buffer = ''

    while (true) {
      const { done, value } = await reader.read()
      if (done) break

      buffer += decoder.decode(value, { stream: true })

      let boundary = buffer.indexOf('\n\n')
      while (boundary !== -1) {
        const event = buffer.slice(0, boundary)
        buffer = buffer.slice(boundary + 2)

        const dataLine = event.split('\n').find(l => l.startsWith('data:'))
        if (dataLine) {
          const raw = dataLine.slice(5)
          let token = raw
          try {
            token = JSON.parse(raw) as string
          } catch {
            // use raw as-is
          }
          if (token.startsWith('STREAMING_ERROR: ')) {
            onError(token.slice('STREAMING_ERROR: '.length))
          } else {
            onToken(token)
          }
        }
        boundary = buffer.indexOf('\n\n')
      }
    }
  } catch (e: any) {
    onError(e?.message || 'Connection error')
  }
  onDone()
}

export function ConversePage() {
  const [messages, setMessages] = useState<Message[]>([])
  const [personas, setPersonas] = useState<Persona[]>([])
  const [models, setModels] = useState<Model[]>([])
  const [selectedPersonaId, setSelectedPersonaId] = useState('')
  const [selectedModelId, setSelectedModelId] = useState('')
  const [input, setInput] = useState('')
  const [streaming, setStreaming] = useState(false)

  // Persona modal
  const [showPersonaModal, setShowPersonaModal] = useState(false)
  const [editingPersona, setEditingPersona] = useState<Persona | null>(null)
  const [personaName, setPersonaName] = useState('')
  const [personaPrompt, setPersonaPrompt] = useState('')
  const [personaSaving, setPersonaSaving] = useState(false)

  const messagesEndRef = useRef<HTMLDivElement>(null)
  const textareaRef = useRef<HTMLTextAreaElement>(null)

  useEffect(() => {
    loadModels()
    loadPersonas()
  }, [])

  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' })
  }, [messages])

  // Auto-grow textarea
  useEffect(() => {
    const ta = textareaRef.current
    if (!ta) return
    ta.style.height = 'auto'
    ta.style.height = Math.min(ta.scrollHeight, 160) + 'px'
  }, [input])

  const loadModels = async () => {
    try {
      const d = await api.converse.getModels()
      setModels(d.models)
      if (d.models.length > 0) setSelectedModelId(d.models[0].id)
    } catch {}
  }

  const loadPersonas = async () => {
    try {
      const d = await api.converse.getPersonas()
      setPersonas(d.personas)
    } catch {}
  }

  const send = async () => {
    const text = input.trim()
    if (!text || streaming) return
    setInput('')
    setStreaming(true)

    const historyForBackend = messages
      .filter(m => !(m.role === 'assistant' && m.content.startsWith('⚠')))
      .map(m => ({ role: m.role, content: m.content }))

    setMessages(prev => [
      ...prev,
      { role: 'user', content: text },
      { role: 'assistant', content: '' },
    ])

    await streamMessage(
      {
        message: text,
        personaId: selectedPersonaId || null,
        modelId: selectedModelId,
        history: historyForBackend,
      },
      token => {
        setMessages(prev => {
          const updated = [...prev]
          const last = updated[updated.length - 1]
          updated[updated.length - 1] = { ...last, content: last.content + token }
          return updated
        })
      },
      errMsg => {
        setMessages(prev => {
          const updated = [...prev]
          updated[updated.length - 1] = { role: 'assistant', content: `⚠ ${errMsg}` }
          return updated
        })
      },
      () => setStreaming(false)
    )
  }

  const newConversation = () => {
    if (messages.length === 0) return
    if (!confirm('Start a new conversation? Current messages will be cleared.')) return
    setMessages([])
  }

  // ── Persona modal actions ────────────────────────────────────────────────

  const openAddPersona = () => {
    setEditingPersona(null)
    setPersonaName('')
    setPersonaPrompt('')
    setShowPersonaModal(true)
  }

  const openEditPersona = (p: Persona) => {
    setEditingPersona(p)
    setPersonaName(p.name)
    setPersonaPrompt(p.systemPrompt)
  }

  const savePersona = async () => {
    if (!personaName.trim()) return
    setPersonaSaving(true)
    try {
      if (editingPersona) {
        await api.converse.updatePersona(editingPersona.id, {
          name: personaName.trim(),
          systemPrompt: personaPrompt,
        })
      } else {
        const created = await api.converse.createPersona(personaName.trim(), personaPrompt)
        setSelectedPersonaId(created.id)
      }
      await loadPersonas()
      setEditingPersona(null)
      setPersonaName('')
      setPersonaPrompt('')
    } catch (e: any) {
      alert(e.message || 'Failed to save persona')
    } finally {
      setPersonaSaving(false)
    }
  }

  const deletePersona = async (id: string) => {
    if (!confirm('Delete this persona?')) return
    try {
      await api.converse.deletePersona(id)
      if (selectedPersonaId === id) setSelectedPersonaId('')
      await loadPersonas()
      if (editingPersona?.id === id) {
        setEditingPersona(null)
        setPersonaName('')
        setPersonaPrompt('')
      }
    } catch (e: any) {
      alert(e.message || 'Failed to delete persona')
    }
  }

  const selectLabel = personas.find(p => p.id === selectedPersonaId)?.name ?? 'No persona'

  return (
    <div className="flex flex-col gap-3" style={{ height: 'calc(100vh - 7rem)' }}>

      {/* Header */}
      <div className="flex-none flex items-center justify-between">
        <h1 className="text-2xl font-semibold">Converse</h1>
        <button
          onClick={newConversation}
          disabled={messages.length === 0}
          className="text-sm px-3 py-1.5 rounded-lg border border-[var(--border)] transition-colors"
          style={{ color: messages.length === 0 ? 'var(--text-muted)' : 'var(--text)', cursor: messages.length === 0 ? 'default' : 'pointer' }}
        >
          New conversation
        </button>
      </div>

      {/* Config bar */}
      <div className="flex-none flex items-center gap-3 flex-wrap">
        <div className="flex items-center gap-2">
          <span className="text-xs text-[var(--text-muted)] font-medium uppercase tracking-wide">Persona</span>
          <select
            value={selectedPersonaId}
            onChange={e => setSelectedPersonaId(e.target.value)}
            className="text-sm px-2.5 py-1.5 border border-[var(--border)] rounded-lg outline-none bg-transparent"
            style={{ color: 'var(--text)' }}
          >
            <option value="">No persona</option>
            {personas.map(p => (
              <option key={p.id} value={p.id}>{p.name}</option>
            ))}
          </select>
          <button
            onClick={openAddPersona}
            className="text-sm text-[var(--accent-sage)] hover:underline whitespace-nowrap"
          >
            Manage
          </button>
        </div>

        <div className="h-4 w-px" style={{ background: 'var(--border-subtle)' }} />

        <div className="flex items-center gap-2">
          <span className="text-xs text-[var(--text-muted)] font-medium uppercase tracking-wide">Model</span>
          <select
            value={selectedModelId}
            onChange={e => setSelectedModelId(e.target.value)}
            className="text-sm px-2.5 py-1.5 border border-[var(--border)] rounded-lg outline-none bg-transparent"
            style={{ color: 'var(--text)' }}
          >
            {models.map(m => (
              <option key={m.id} value={m.id}>{m.name}</option>
            ))}
          </select>
        </div>
      </div>

      {/* Message thread */}
      <div className="flex-1 overflow-y-auto rounded-xl border border-[var(--border)] bg-[var(--bg)] px-4 py-4 space-y-4">
        {messages.length === 0 && (
          <div className="h-full flex flex-col items-center justify-center text-center py-16">
            <div className="text-4xl mb-3">💬</div>
            <p className="text-[var(--text-muted)]">Start a conversation</p>
            {selectedPersonaId && (
              <p className="text-xs text-[var(--text-muted)] mt-1">
                Talking to: <span className="font-medium">{selectLabel}</span>
              </p>
            )}
            {!selectedPersonaId && (
              <p className="text-xs text-[var(--text-muted)] mt-1">
                Pick a persona above or start chatting directly
              </p>
            )}
          </div>
        )}

        {messages.map((msg, i) => (
          <div key={i} className={`flex ${msg.role === 'user' ? 'justify-end' : 'justify-start'}`}>
            <div
              className={`max-w-[75%] px-4 py-2.5 rounded-2xl text-sm leading-relaxed ${
                msg.role === 'user'
                  ? 'rounded-tr-sm text-white'
                  : 'rounded-tl-sm border border-[var(--border)] bg-white'
              }`}
              style={msg.role === 'user' ? { background: 'var(--accent-sage)' } : { color: 'var(--text)' }}
            >
              {msg.content === '' && streaming && i === messages.length - 1 ? (
                <span className="inline-block w-1.5 h-4 rounded-sm animate-pulse" style={{ background: 'var(--text-muted)' }} />
              ) : msg.role === 'user' ? (
                <span style={{ whiteSpace: 'pre-wrap' }}>{msg.content}</span>
              ) : (
                <div className="markdown-body">
                  <ReactMarkdown
                    remarkPlugins={[remarkGfm]}
                    components={{
                      h1: ({ children }) => <h1 className="text-lg font-bold mt-3 mb-1">{children}</h1>,
                      h2: ({ children }) => <h2 className="text-base font-bold mt-3 mb-1">{children}</h2>,
                      h3: ({ children }) => <h3 className="text-sm font-bold mt-2 mb-1">{children}</h3>,
                      p: ({ children }) => <p className="mb-2 last:mb-0">{children}</p>,
                      ul: ({ children }) => <ul className="list-disc list-outside ml-4 mb-2 space-y-0.5">{children}</ul>,
                      ol: ({ children }) => <ol className="list-decimal list-outside ml-4 mb-2 space-y-0.5">{children}</ol>,
                      li: ({ children }) => <li className="text-sm">{children}</li>,
                      strong: ({ children }) => <strong className="font-semibold">{children}</strong>,
                      em: ({ children }) => <em className="italic">{children}</em>,
                      code: ({ children, className }) => {
                        const isBlock = className?.includes('language-')
                        return isBlock ? (
                          <code className="block">{children}</code>
                        ) : (
                          <code
                            className="px-1.5 py-0.5 rounded text-xs font-mono"
                            style={{ background: 'var(--bg)', color: 'var(--accent-sage)', border: '1px solid var(--border)' }}
                          >{children}</code>
                        )
                      },
                      pre: ({ children }) => (
                        <pre
                          className="text-xs font-mono rounded-xl p-3 my-2 overflow-x-auto"
                          style={{ background: 'var(--bg)', border: '1px solid var(--border)', color: 'var(--text)' }}
                        >{children}</pre>
                      ),
                      blockquote: ({ children }) => (
                        <blockquote
                          className="pl-3 my-2 text-sm italic"
                          style={{ borderLeft: '3px solid var(--accent-sage)', color: 'var(--text-muted)' }}
                        >{children}</blockquote>
                      ),
                      table: ({ children }) => (
                        <div className="overflow-x-auto my-2">
                          <table className="text-xs w-full border-collapse">{children}</table>
                        </div>
                      ),
                      th: ({ children }) => (
                        <th
                          className="px-3 py-1.5 text-left font-semibold text-xs"
                          style={{ background: 'var(--bg)', borderBottom: '2px solid var(--border)' }}
                        >{children}</th>
                      ),
                      td: ({ children }) => (
                        <td
                          className="px-3 py-1.5 text-xs"
                          style={{ borderBottom: '1px solid var(--border-subtle)' }}
                        >{children}</td>
                      ),
                      hr: () => <hr className="my-3" style={{ borderColor: 'var(--border-subtle)' }} />,
                    }}
                  >
                    {msg.content}
                  </ReactMarkdown>
                  {streaming && i === messages.length - 1 && (
                    <span className="inline-block w-0.5 h-4 ml-0.5 align-middle rounded-full animate-pulse" style={{ background: 'var(--text-muted)' }} />
                  )}
                </div>
              )}
            </div>
          </div>
        ))}

        <div ref={messagesEndRef} />
      </div>

      {/* Input area */}
      <div className="flex-none flex gap-2 items-end">
        <textarea
          ref={textareaRef}
          value={input}
          onChange={e => setInput(e.target.value)}
          onKeyDown={e => {
            if (e.key === 'Enter' && !e.shiftKey) {
              e.preventDefault()
              send()
            }
          }}
          rows={1}
          disabled={streaming}
          placeholder="Send a message… (Enter to send, Shift+Enter for new line)"
          className="flex-1 px-4 py-2.5 border border-[var(--border)] rounded-xl outline-none text-sm resize-none overflow-hidden transition-colors"
          style={{
            background: streaming ? 'var(--bg)' : 'white',
            color: 'var(--text)',
          }}
        />
        <button
          onClick={send}
          disabled={streaming || !input.trim()}
          className="flex-none w-10 h-10 rounded-xl flex items-center justify-center transition-all"
          style={{
            background: streaming || !input.trim() ? 'var(--border)' : 'var(--accent-sage)',
            color: streaming || !input.trim() ? 'var(--text-muted)' : 'white',
          }}
        >
          <svg viewBox="0 0 16 16" fill="none" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round" className="w-4 h-4">
            <path d="M13 8L3 2.5l2.5 5.5L3 13.5z" />
          </svg>
        </button>
      </div>

      {/* Persona Management Modal */}
      {showPersonaModal && (
        <div
          className="fixed inset-0 z-50 flex items-center justify-center px-4"
          style={{ background: 'rgba(0,0,0,0.35)' }}
          onClick={e => { if (e.target === e.currentTarget) setShowPersonaModal(false) }}
        >
          <div className="bg-white rounded-2xl w-full max-w-lg shadow-xl overflow-hidden">
            {/* Modal header */}
            <div className="flex items-center justify-between px-5 py-4 border-b border-[var(--border)]">
              <h2 className="font-semibold text-base">Manage Personas</h2>
              <button
                onClick={() => setShowPersonaModal(false)}
                className="text-[var(--text-muted)] hover:text-[var(--text)] text-lg leading-none"
              >
                ✕
              </button>
            </div>

            <div className="p-5 space-y-5 max-h-[70vh] overflow-y-auto">
              {/* Existing personas */}
              {personas.length > 0 && (
                <div className="space-y-1.5">
                  {personas.map(p => (
                    <div
                      key={p.id}
                      className={`flex items-center gap-3 px-3 py-2.5 rounded-lg border transition-colors ${
                        editingPersona?.id === p.id
                          ? 'border-[var(--accent-sage)] bg-[var(--accent-sage-light)]'
                          : 'border-[var(--border)] bg-[var(--bg)]'
                      }`}
                    >
                      <div className="flex-1 min-w-0">
                        <div className="text-sm font-medium truncate">{p.name}</div>
                        {p.systemPrompt && (
                          <div className="text-xs text-[var(--text-muted)] truncate mt-0.5">{p.systemPrompt}</div>
                        )}
                      </div>
                      <div className="flex items-center gap-1.5 flex-shrink-0">
                        <button
                          onClick={() => openEditPersona(p)}
                          className="text-xs px-2 py-1 rounded-md text-[var(--text-muted)] hover:text-[var(--text)] hover:bg-[var(--bg)] transition-colors"
                        >
                          Edit
                        </button>
                        <button
                          onClick={() => deletePersona(p.id)}
                          className="text-xs px-2 py-1 rounded-md text-[var(--text-muted)] hover:text-[var(--danger)] transition-colors"
                        >
                          ✕
                        </button>
                      </div>
                    </div>
                  ))}
                </div>
              )}

              {personas.length === 0 && !editingPersona && (
                <p className="text-sm text-[var(--text-muted)] text-center py-2">No personas yet. Create one below.</p>
              )}

              {/* Add / Edit form */}
              <div className="border-t border-[var(--border-subtle)] pt-4 space-y-3">
                <h3 className="text-sm font-medium" style={{ color: 'var(--text)' }}>
                  {editingPersona ? `Edit "${editingPersona.name}"` : 'New Persona'}
                </h3>
                <input
                  value={personaName}
                  onChange={e => setPersonaName(e.target.value)}
                  placeholder="Persona name (e.g. Life Coach, Code Reviewer)"
                  className="w-full px-3 py-2 border border-[var(--border)] rounded-lg outline-none text-sm"
                />
                <textarea
                  value={personaPrompt}
                  onChange={e => setPersonaPrompt(e.target.value)}
                  placeholder="System prompt — describe how the AI should behave, its personality, expertise, tone…"
                  rows={5}
                  className="w-full px-3 py-2 border border-[var(--border)] rounded-lg outline-none text-sm resize-none"
                />
                <div className="flex items-center gap-2">
                  <button
                    onClick={savePersona}
                    disabled={!personaName.trim() || personaSaving}
                    className="px-4 py-2 rounded-lg text-sm text-white transition-opacity"
                    style={{ background: 'var(--accent-sage)', opacity: !personaName.trim() || personaSaving ? 0.6 : 1 }}
                  >
                    {personaSaving ? 'Saving…' : editingPersona ? 'Save changes' : 'Create persona'}
                  </button>
                  {editingPersona && (
                    <button
                      onClick={() => { setEditingPersona(null); setPersonaName(''); setPersonaPrompt('') }}
                      className="px-4 py-2 rounded-lg text-sm border border-[var(--border)] text-[var(--text-muted)] hover:text-[var(--text)]"
                    >
                      Cancel
                    </button>
                  )}
                </div>
              </div>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}
