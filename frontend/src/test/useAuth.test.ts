import { describe, it, expect, vi, beforeEach } from 'vitest'
import { renderHook, act, waitFor } from '@testing-library/react'
import { useAuth } from '../hooks/useAuth'

const mockFetch = vi.fn()
global.fetch = mockFetch

beforeEach(() => { mockFetch.mockReset() })

describe('useAuth', () => {
  it('starts with null (loading)', () => {
    mockFetch.mockResolvedValueOnce({ ok: false, status: 401 })
    const { result } = renderHook(() => useAuth())
    expect(result.current.authenticated).toBeNull()
  })

  it('sets authenticated=true when check succeeds', async () => {
    mockFetch.mockResolvedValueOnce({ ok: true, json: () => Promise.resolve({ authenticated: true }) })
    const { result } = renderHook(() => useAuth())
    await waitFor(() => expect(result.current.authenticated).toBe(true))
  })

  it('sets authenticated=false when check fails', async () => {
    mockFetch.mockResolvedValueOnce({ ok: false, status: 401 })
    const { result } = renderHook(() => useAuth())
    await waitFor(() => expect(result.current.authenticated).toBe(false))
  })

  it('login sets authenticated=true on success', async () => {
    mockFetch.mockResolvedValueOnce({ ok: false, status: 401 }) // initial check
    mockFetch.mockResolvedValueOnce({ ok: true, json: () => Promise.resolve({ message: 'ok' }) }) // login
    const { result } = renderHook(() => useAuth())
    await waitFor(() => expect(result.current.authenticated).toBe(false))
    let success: boolean = false
    await act(async () => { success = await result.current.login('dev') })
    expect(success).toBe(true)
    expect(result.current.authenticated).toBe(true)
  })

  it('login returns false on failure', async () => {
    mockFetch.mockResolvedValueOnce({ ok: false, status: 401 }) // initial check
    mockFetch.mockResolvedValueOnce({ ok: false, status: 401 }) // login
    const { result } = renderHook(() => useAuth())
    await waitFor(() => expect(result.current.authenticated).toBe(false))
    let success: boolean = true
    await act(async () => { success = await result.current.login('wrong') })
    expect(success).toBe(false)
  })

  it('logout sets authenticated=false', async () => {
    mockFetch.mockResolvedValueOnce({ ok: true, json: () => Promise.resolve({ authenticated: true }) }) // check
    mockFetch.mockResolvedValueOnce({ ok: true, status: 200, json: () => Promise.resolve({ message: 'ok' }) }) // logout
    const { result } = renderHook(() => useAuth())
    await waitFor(() => expect(result.current.authenticated).toBe(true))
    await act(async () => { await result.current.logout() })
    expect(result.current.authenticated).toBe(false)
  })
})
