import { useState } from 'react'

export function LoginPage({ onLogin }: { onLogin: (pw: string) => Promise<boolean> }) {
  const [password, setPassword] = useState('')
  const [error, setError] = useState(false)
  const [loading, setLoading] = useState(false)

  const submit = async (e: React.FormEvent) => {
    e.preventDefault()
    setLoading(true)
    const ok = await onLogin(password)
    setLoading(false)
    if (!ok) setError(true)
  }

  return (
    <div className="min-h-screen flex items-center justify-center bg-[var(--bg)]">
      <form onSubmit={submit} className="bg-white p-8 rounded-2xl shadow-sm border border-[var(--border)] w-80">
        <div className="flex justify-center mb-5">
          <div className="w-12 h-12 rounded-xl bg-[var(--accent-sage)] flex items-center justify-center text-white text-xl font-bold shadow-sm">
            L
          </div>
        </div>
        <h1 className="text-lg font-semibold mb-1 text-center">LifeApp</h1>
        <p className="text-xs text-[var(--text-muted)] text-center mb-6">Your calm productivity space</p>
        <input type="password" value={password} onChange={e => { setPassword(e.target.value); setError(false) }}
          placeholder="Password" autoFocus
          className="w-full px-3 py-2.5 border border-[var(--border)] rounded-lg mb-4 outline-none text-sm" />
        {error && <p className="text-[var(--danger)] text-xs mb-3 text-center">Invalid password</p>}
        <button type="submit" disabled={loading}
          className="w-full py-2.5 bg-[var(--accent-sage)] text-white rounded-lg text-sm font-medium hover:opacity-90 transition-opacity disabled:opacity-50">
          {loading ? 'Signing in...' : 'Sign in'}
        </button>
      </form>
    </div>
  )
}
