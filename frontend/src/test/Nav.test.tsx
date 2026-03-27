import { describe, it, expect, vi } from 'vitest'
import { render, screen, fireEvent } from '@testing-library/react'
import { Sidebar, BottomNav } from '../components/layout/Nav'

describe('Sidebar', () => {
  it('renders all module icons', () => {
    render(<Sidebar current="inbox" onNavigate={vi.fn()} onLogout={vi.fn()} />)
    const buttons = screen.getAllByRole('button')
    expect(buttons.length).toBe(9) // 8 nav items + logout
  })

  it('highlights current page', () => {
    render(<Sidebar current="inbox" onNavigate={vi.fn()} onLogout={vi.fn()} />)
    const inboxBtn = screen.getAllByRole('button')[0]
    expect(inboxBtn.className).toContain('accent-sage')
  })

  it('calls onNavigate when clicked', () => {
    const onNavigate = vi.fn()
    render(<Sidebar current="inbox" onNavigate={onNavigate} onLogout={vi.fn()} />)
    fireEvent.click(screen.getAllByRole('button')[1]) // projects
    expect(onNavigate).toHaveBeenCalledWith('projects')
  })
})

describe('BottomNav', () => {
  it('renders all module buttons', () => {
    render(<BottomNav current="inbox" onNavigate={vi.fn()} />)
    const buttons = screen.getAllByRole('button')
    expect(buttons.length).toBe(8) // 8 nav items
  })

  it('highlights current page', () => {
    render(<BottomNav current="projects" onNavigate={vi.fn()} />)
    const projectsBtn = screen.getAllByRole('button')[1]
    expect(projectsBtn.className).toContain('accent-sage')
  })
})
