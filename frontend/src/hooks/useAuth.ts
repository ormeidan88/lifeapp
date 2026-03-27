import { useState, useEffect } from 'react'
import { api } from '../api/client'

export function useAuth() {
  const [authenticated, setAuthenticated] = useState<boolean | null>(null)

  useEffect(() => {
    api.auth.check()
      .then(() => setAuthenticated(true))
      .catch(() => setAuthenticated(false))
  }, [])

  const login = async (password: string) => {
    try {
      await api.auth.login(password)
      setAuthenticated(true)
      return true
    } catch { return false }
  }

  const logout = async () => {
    await api.auth.logout()
    setAuthenticated(false)
  }

  return { authenticated, login, logout }
}
