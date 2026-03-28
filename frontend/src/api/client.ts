const API = '/api'

async function request(path: string, options?: RequestInit) {
  const res = await fetch(`${API}${path}`, {
    credentials: 'include',
    headers: { 'Content-Type': 'application/json' },
    ...options,
  })
  if (res.status === 401) {
    window.location.href = '/login'
    throw new Error('Unauthorized')
  }
  if (res.status === 204) return null
  const data = await res.json()
  if (!res.ok) throw new Error(data.error || `Request failed: ${res.status}`)
  return data
}

export const api = {
  auth: {
    login: async (password: string) => {
      const res = await fetch(`${API}/auth/login`, {
        method: 'POST', credentials: 'include',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ password }),
      })
      if (!res.ok) throw new Error('Invalid password')
      return res.json()
    },
    logout: () => request('/auth/logout', { method: 'POST' }),
    check: async () => {
      const res = await fetch(`${API}/auth/check`, { credentials: 'include' })
      if (!res.ok) throw new Error('Not authenticated')
      return res.json()
    },
  },
  inbox: {
    list: () => request('/inbox'),
    create: (text: string) => request('/inbox', { method: 'POST', body: JSON.stringify({ text }) }),
    delete: (id: string) => request(`/inbox/${id}`, { method: 'DELETE' }),
    convert: (id: string, body: any) => request(`/inbox/${id}/convert`, { method: 'POST', body: JSON.stringify(body) }),
  },
  projects: {
    list: (status?: string) => request(`/projects${status ? `?status=${status}` : ''}`),
    get: (id: string) => request(`/projects/${id}`),
    create: (name: string, description?: string) => request('/projects', { method: 'POST', body: JSON.stringify({ name, description }) }),
    update: (id: string, body: any) => request(`/projects/${id}`, { method: 'PATCH', body: JSON.stringify(body) }),
    delete: (id: string) => request(`/projects/${id}`, { method: 'DELETE' }),
  },
  tasks: {
    list: (projectId: string) => request(`/tasks?projectId=${projectId}`),
    create: (body: any) => request('/tasks', { method: 'POST', body: JSON.stringify(body) }),
    update: (id: string, body: any) => request(`/tasks/${id}?projectId=${body.projectId}`, { method: 'PATCH', body: JSON.stringify(body) }),
    reorder: (projectId: string, taskIds: string[]) => request('/tasks/reorder', { method: 'POST', body: JSON.stringify({ projectId, taskIds }) }),
    delete: (id: string, projectId: string) => request(`/tasks/${id}?projectId=${projectId}`, { method: 'DELETE' }),
  },
  lists: {
    list: () => request('/lists'),
    create: (name: string) => request('/lists', { method: 'POST', body: JSON.stringify({ name, type: 'CUSTOM' }) }),
    addItem: (listId: string, taskId: string) => request(`/lists/${listId}/items`, { method: 'POST', body: JSON.stringify({ taskId }) }),
    getItems: (listId: string) => request(`/lists/${listId}/items`),
    removeItem: (listId: string, taskId: string) => request(`/lists/${listId}/items/${taskId}`, { method: 'DELETE' }),
    delete: (id: string) => request(`/lists/${id}`, { method: 'DELETE' }),
  },
  pages: {
    list: (params?: { ownerType?: string; ownerId?: string; parentPageId?: string }) => {
      const q = new URLSearchParams(params as any).toString()
      return request(`/pages?${q}`)
    },
    get: (id: string) => request(`/pages/${id}`),
    create: (body: any) => request('/pages', { method: 'POST', body: JSON.stringify(body) }),
    update: (id: string, body: any) => request(`/pages/${id}`, { method: 'PATCH', body: JSON.stringify(body) }),
    delete: (id: string) => request(`/pages/${id}`, { method: 'DELETE' }),
    search: (q: string) => request(`/pages/search?q=${encodeURIComponent(q)}`),
  },
  habits: {
    list: () => request('/habits'),
    create: (name: string, color?: string) => request('/habits', { method: 'POST', body: JSON.stringify({ name, color }) }),
    update: (id: string, body: any) => request(`/habits/${id}`, { method: 'PATCH', body: JSON.stringify(body) }),
    delete: (id: string) => request(`/habits/${id}`, { method: 'DELETE' }),
    createEntry: (id: string, date: string, value: string) => request(`/habits/${id}/entries`, { method: 'POST', body: JSON.stringify({ date, value }) }),
    getEntries: (id: string, from: string, to: string) => request(`/habits/${id}/entries?from=${from}&to=${to}`),
  },
  decks: {
    list: () => request('/decks'),
    create: (name: string) => request('/decks', { method: 'POST', body: JSON.stringify({ name }) }),
    delete: (id: string) => request(`/decks/${id}`, { method: 'DELETE' }),
    getCards: (deckId: string) => request(`/decks/${deckId}/cards`),
    createCard: (deckId: string, front: string, back: string) => request(`/decks/${deckId}/cards`, { method: 'POST', body: JSON.stringify({ front, back }) }),
    getReview: (deckId: string) => request(`/decks/${deckId}/review`),
    review: (deckId: string, cardId: string, rating: string) => request(`/decks/${deckId}/cards/${cardId}/review`, { method: 'POST', body: JSON.stringify({ rating }) }),
  },
  books: {
    search: (q: string) => request(`/books/search?q=${encodeURIComponent(q)}`),
    list: () => request('/books'),
    create: (body: any) => request('/books', { method: 'POST', body: JSON.stringify(body) }),
    delete: (id: string) => request(`/books/${id}`, { method: 'DELETE' }),
  },
  calendar: {
    list: (from: string, to: string) => request(`/calendar/events?from=${from}&to=${to}`),
    create: (body: any) => request('/calendar/events', { method: 'POST', body: JSON.stringify(body) }),
    update: (id: string, body: any) => request(`/calendar/events/${id}`, { method: 'PATCH', body: JSON.stringify(body) }),
    delete: (id: string) => request(`/calendar/events/${id}`, { method: 'DELETE' }),
  },
  images: {
    upload: async (file: File) => {
      const form = new FormData()
      form.append('file', file)
      const res = await fetch(`${API}/images`, { method: 'POST', credentials: 'include', body: form })
      return res.json()
    },
  },
  waitingFor: {
    list: () => request('/waiting-for'),
    create: (description: string, waitingFor: string, dueDate: string) => request('/waiting-for', { method: 'POST', body: JSON.stringify({ description, waitingFor, dueDate }) }),
    delete: (id: string) => request(`/waiting-for/${id}`, { method: 'DELETE' }),
    acknowledge: (id: string) => request(`/waiting-for/${id}/acknowledge`, { method: 'POST' }),
    overdueCount: () => request('/waiting-for/overdue-count'),
  },
}
