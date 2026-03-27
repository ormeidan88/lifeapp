import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, fireEvent, waitFor } from '@testing-library/react'
import { LoginPage } from '../pages/LoginPage'

describe('LoginPage', () => {
  it('renders password input and sign in button', () => {
    render(<LoginPage onLogin={vi.fn()} />)
    expect(screen.getByPlaceholderText('Password')).toBeInTheDocument()
    expect(screen.getByText('Sign in')).toBeInTheDocument()
  })

  it('calls onLogin with password', async () => {
    const onLogin = vi.fn().mockResolvedValue(true)
    render(<LoginPage onLogin={onLogin} />)
    fireEvent.change(screen.getByPlaceholderText('Password'), { target: { value: 'dev' } })
    fireEvent.click(screen.getByText('Sign in'))
    await waitFor(() => expect(onLogin).toHaveBeenCalledWith('dev'))
  })

  it('shows error on failed login', async () => {
    const onLogin = vi.fn().mockResolvedValue(false)
    render(<LoginPage onLogin={onLogin} />)
    fireEvent.change(screen.getByPlaceholderText('Password'), { target: { value: 'wrong' } })
    fireEvent.click(screen.getByText('Sign in'))
    await waitFor(() => expect(screen.getByText('Invalid password')).toBeInTheDocument())
  })

  it('password input has autoFocus', () => {
    render(<LoginPage onLogin={vi.fn()} />)
    expect(screen.getByPlaceholderText('Password')).toHaveFocus()
  })
})
