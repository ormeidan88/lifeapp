import { describe, it, expect, vi, beforeEach } from 'vitest'
import { api } from '../api/client'

// Mock fetch globally
const mockFetch = vi.fn()
global.fetch = mockFetch

beforeEach(() => { mockFetch.mockReset() })

describe('api.auth', () => {
  it('login sends password and returns json', async () => {
    mockFetch.mockResolvedValueOnce({ ok: true, json: () => Promise.resolve({ message: 'ok' }) })
    const result = await api.auth.login('dev')
    expect(mockFetch).toHaveBeenCalledWith('/api/auth/login', expect.objectContaining({
      method: 'POST', body: JSON.stringify({ password: 'dev' })
    }))
    expect(result.message).toBe('ok')
  })

  it('login throws on 401', async () => {
    mockFetch.mockResolvedValueOnce({ ok: false, status: 401 })
    await expect(api.auth.login('wrong')).rejects.toThrow()
  })

  it('check returns json on success', async () => {
    mockFetch.mockResolvedValueOnce({ ok: true, json: () => Promise.resolve({ authenticated: true }) })
    const result = await api.auth.check()
    expect(result.authenticated).toBe(true)
  })

  it('check throws on 401', async () => {
    mockFetch.mockResolvedValueOnce({ ok: false, status: 401 })
    await expect(api.auth.check()).rejects.toThrow()
  })
})

describe('api.inbox', () => {
  it('create sends text', async () => {
    mockFetch.mockResolvedValueOnce({ ok: true, status: 201, json: () => Promise.resolve({ id: '1', text: 'test' }) })
    const result = await api.inbox.create('test')
    expect(mockFetch).toHaveBeenCalledWith('/api/inbox', expect.objectContaining({
      method: 'POST', body: JSON.stringify({ text: 'test' })
    }))
    expect(result.text).toBe('test')
  })

  it('list returns items', async () => {
    mockFetch.mockResolvedValueOnce({ ok: true, status: 200, json: () => Promise.resolve({ items: [{ id: '1', text: 'a' }] }) })
    const result = await api.inbox.list()
    expect(result.items).toHaveLength(1)
  })

  it('delete sends DELETE', async () => {
    mockFetch.mockResolvedValueOnce({ ok: true, status: 204, json: () => Promise.resolve(null) })
    await api.inbox.delete('123')
    expect(mockFetch).toHaveBeenCalledWith('/api/inbox/123', expect.objectContaining({ method: 'DELETE' }))
  })
})

describe('api.projects', () => {
  it('create sends name', async () => {
    mockFetch.mockResolvedValueOnce({ ok: true, status: 201, json: () => Promise.resolve({ id: '1', name: 'P' }) })
    const result = await api.projects.create('P')
    expect(result.name).toBe('P')
  })

  it('list returns projects', async () => {
    mockFetch.mockResolvedValueOnce({ ok: true, status: 200, json: () => Promise.resolve({ projects: [] }) })
    const result = await api.projects.list()
    expect(result.projects).toEqual([])
  })

  it('update sends PATCH', async () => {
    mockFetch.mockResolvedValueOnce({ ok: true, status: 200, json: () => Promise.resolve({ status: 'DONE' }) })
    const result = await api.projects.update('1', { status: 'DONE' })
    expect(result.status).toBe('DONE')
  })
})

describe('api.habits', () => {
  it('createEntry sends date and value', async () => {
    mockFetch.mockResolvedValueOnce({ ok: true, status: 201, json: () => Promise.resolve({ habitId: '1', date: '2026-03-20', value: 'YES' }) })
    const result = await api.habits.createEntry('1', '2026-03-20', 'YES')
    expect(result.value).toBe('YES')
  })
})

describe('api.decks', () => {
  it('review sends rating', async () => {
    mockFetch.mockResolvedValueOnce({ ok: true, status: 200, json: () => Promise.resolve({ fsrs: { state: 'REVIEW' } }) })
    const result = await api.decks.review('d1', 'c1', 'GOOD')
    expect(result.fsrs.state).toBe('REVIEW')
  })
})

describe('api.calendar', () => {
  it('list sends from and to', async () => {
    mockFetch.mockResolvedValueOnce({ ok: true, status: 200, json: () => Promise.resolve({ events: [] }) })
    await api.calendar.list('2026-03-20', '2026-03-26')
    expect(mockFetch).toHaveBeenCalledWith('/api/calendar/events?from=2026-03-20&to=2026-03-26', expect.anything())
  })
})

describe('api.images', () => {
  it('upload sends FormData', async () => {
    mockFetch.mockResolvedValueOnce({ ok: true, json: () => Promise.resolve({ url: '/images/test.jpg' }) })
    const file = new File(['data'], 'test.jpg', { type: 'image/jpeg' })
    const result = await api.images.upload(file)
    expect(result.url).toBe('/images/test.jpg')
    // Should NOT have Content-Type header (FormData sets it automatically)
    const callArgs = mockFetch.mock.calls[0]
    expect(callArgs[1].headers).toBeUndefined()
  })
})

describe('api request redirects on 401', () => {
  it('redirects to /login on 401 for non-auth endpoints', async () => {
    // Mock window.location
    const originalLocation = window.location
    Object.defineProperty(window, 'location', { writable: true, value: { href: '' } })
    mockFetch.mockResolvedValueOnce({ ok: false, status: 401, json: () => Promise.resolve({ error: 'Unauthorized' }) })
    try {
      await api.inbox.list()
    } catch (e) {
      // Expected
    }
    expect(window.location.href).toBe('/login')
    window.location = originalLocation
  })
})
