import { describe, it, expect, vi, beforeEach } from 'vitest'
import { api } from '../api/client'

const mockFetch = vi.fn()
global.fetch = mockFetch

beforeEach(() => { mockFetch.mockReset() })

const ok = (data: any, status = 200) => mockFetch.mockResolvedValueOnce({ ok: true, status, json: () => Promise.resolve(data) })
const ok204 = () => mockFetch.mockResolvedValueOnce({ ok: true, status: 204, json: () => Promise.resolve(null) })

describe('api.tasks', () => {
  it('list', async () => { ok({ tasks: [] }); const r = await api.tasks.list('p1'); expect(r.tasks).toEqual([]) })
  it('create', async () => { ok({ id: '1' }, 201); await api.tasks.create({ projectId: 'p1', title: 'T' }) })
  it('update', async () => { ok({ done: true }); await api.tasks.update('t1', { projectId: 'p1', done: true }) })
  it('reorder', async () => { ok({ tasks: [] }); await api.tasks.reorder('p1', ['t1', 't2']) })
  it('delete', async () => { ok204(); await api.tasks.delete('t1', 'p1') })
})

describe('api.lists', () => {
  it('list', async () => { ok({ lists: [] }); const r = await api.lists.list(); expect(r.lists).toEqual([]) })
  it('create', async () => { ok({ id: '1' }, 201); await api.lists.create('Today') })
  it('addItem', async () => { ok({}, 201); await api.lists.addItem('l1', 't1') })
  it('getItems', async () => { ok({ items: [] }); await api.lists.getItems('l1') })
  it('removeItem', async () => { ok204(); await api.lists.removeItem('l1', 't1') })
  it('delete', async () => { ok204(); await api.lists.delete('l1') })
})

describe('api.pages', () => {
  it('list', async () => { ok({ pages: [] }); await api.pages.list({ ownerType: 'standalone' }) })
  it('get', async () => { ok({ id: '1', content: {} }); await api.pages.get('p1') })
  it('create', async () => { ok({ id: '1' }, 201); await api.pages.create({ title: 'T', ownerType: 'standalone' }) })
  it('update', async () => { ok({ id: '1' }); await api.pages.update('p1', { title: 'New' }) })
  it('delete', async () => { ok204(); await api.pages.delete('p1') })
})

describe('api.habits', () => {
  it('list', async () => { ok({ habits: [] }); await api.habits.list() })
  it('create', async () => { ok({ id: '1' }, 201); await api.habits.create('Meditate') })
  it('update', async () => { ok({}); await api.habits.update('h1', { name: 'New' }) })
  it('delete', async () => { ok204(); await api.habits.delete('h1') })
  it('getEntries', async () => { ok({ entries: [] }); await api.habits.getEntries('h1', '2026-03-01', '2026-03-31') })
})

describe('api.decks', () => {
  it('list', async () => { ok({ decks: [] }); await api.decks.list() })
  it('create', async () => { ok({ id: '1' }, 201); await api.decks.create('Spanish') })
  it('delete', async () => { ok204(); await api.decks.delete('d1') })
  it('getCards', async () => { ok({ cards: [] }); await api.decks.getCards('d1') })
  it('createCard', async () => { ok({ id: '1' }, 201); await api.decks.createCard('d1', 'Q', 'A') })
  it('getReview', async () => { ok({ cards: [] }); await api.decks.getReview('d1') })
})

describe('api.books', () => {
  it('list', async () => { ok({ books: [] }); await api.books.list() })
  it('create', async () => { ok({ id: '1' }, 201); await api.books.create({ googleBooksId: 'g1', title: 'B' }) })
  it('delete', async () => { ok204(); await api.books.delete('b1') })
  it('search', async () => { ok({ results: [] }); await api.books.search('test') })
})

describe('api.calendar', () => {
  it('create', async () => { ok({ id: '1' }, 201); await api.calendar.create({ title: 'E', date: '2026-03-20' }) })
  it('update', async () => { ok({}); await api.calendar.update('e1', { title: 'New' }) })
  it('delete', async () => { ok204(); await api.calendar.delete('e1') })
})

describe('api.inbox.convert', () => {
  it('convert', async () => { ok({ id: '1', type: 'task', inboxItemDeleted: true }, 201); await api.inbox.convert('i1', { targetType: 'task', projectId: 'p1' }) })
})

describe('api.projects additional', () => {
  it('get', async () => { ok({ id: '1', name: 'P' }); await api.projects.get('p1') })
  it('delete', async () => { ok204(); await api.projects.delete('p1') })
})
