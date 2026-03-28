import { useState, useEffect } from 'react'
import { useAuth } from './hooks/useAuth'
import { Sidebar, BottomNav } from './components/layout/Nav'
import { LoginPage } from './pages/LoginPage'
import { InboxPage } from './pages/InboxPage'
import { ProjectsPage } from './pages/ProjectsPage'
import { ProjectDetailPage } from './pages/ProjectDetailPage'
import { PagesPage } from './pages/PagesPage'
import { ListsPage } from './pages/ListsPage'
import { HabitsPage } from './pages/HabitsPage'
import { MemorizePage } from './pages/MemorizePage'
import { BooksPage } from './pages/BooksPage'
import { CalendarPage } from './pages/CalendarPage'
import { ConversePage } from './pages/ConversePage'

export default function App() {
  const { authenticated, login, logout } = useAuth()
  const [page, setPage] = useState('inbox')
  const [projectId, setProjectId] = useState<string | null>(null)
  const [pageId, setPageId] = useState<string | null>(null)

  // Listen for page link navigation from TipTap editor
  useEffect(() => {
    const handler = (e: Event) => {
      const detail = (e as CustomEvent).detail
      if (detail?.pageId) {
        setPageId(detail.pageId)
        setPage('page-detail')
      }
    }
    window.addEventListener('navigate-page', handler)
    return () => window.removeEventListener('navigate-page', handler)
  }, [])

  if (authenticated === null) return <div className="min-h-screen flex items-center justify-center text-[var(--text-muted)]">Loading...</div>
  if (!authenticated) return <LoginPage onLogin={login} />

  const openProject = (id: string) => { setProjectId(id); setPage('project-detail') }

  const renderPage = () => {
    switch (page) {
      case 'inbox': return <InboxPage />
      case 'projects': return <ProjectsPage onOpen={openProject} />
      case 'project-detail': return projectId ? <ProjectDetailPage projectId={projectId} onBack={() => setPage('projects')} /> : null
      case 'lists': return <ListsPage />
      case 'pages': return <PagesPage externalPageId={pageId} onClearExternal={() => setPageId(null)} />
      case 'page-detail': return <PagesPage externalPageId={pageId} onClearExternal={() => { setPageId(null); setPage('pages') }} />
      case 'habits': return <HabitsPage />
      case 'memorize': return <MemorizePage />
      case 'books': return <BooksPage />
      case 'calendar': return <CalendarPage />
      case 'converse': return <ConversePage />
      default: return <InboxPage />
    }
  }

  return (
    <div className="min-h-screen">
      <Sidebar current={page === 'page-detail' ? 'pages' : page} onNavigate={(p) => { setPageId(null); setPage(p) }} onLogout={logout} />
      <BottomNav current={page === 'page-detail' ? 'pages' : page} onNavigate={(p) => { setPageId(null); setPage(p) }} />
      <main className="md:ml-48 px-6 py-7 pb-24 md:pb-8">
        <div key={page} className="max-w-6xl mx-auto w-full page-transition">
          {renderPage()}
        </div>
      </main>
    </div>
  )
}
