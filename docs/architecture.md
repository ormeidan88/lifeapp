# Architecture: LifeApp

## Overview

A personal productivity monolith — Java 21 / Quarkus backend + React SPA frontend. Single-user, deployed as a fat jar on EC2 behind Route53/ALB.

## Backend

- **Framework:** Quarkus 3.17 with RESTEasy Reactive
- **Database:** DynamoDB (multi-table, 13 tables, on-demand capacity)
- **Auth:** BCrypt password hash from SSM (or properties in dev), encrypted session cookie + API key
- **Package structure:**
  - `app.auth` — AuthResource, SessionManager, SessionFilter
  - `app.config` — AuthConfig (SSM/properties bridge)
  - `app.model` — DynamoDB bean classes (13 entities)
  - `app.resource` — REST endpoints (11 resource classes)
  - `app.dynamo` — Tables producer, TableInitializer, SeedData

## Frontend

- **Framework:** React 19 + TypeScript + Vite
- **Editor:** TipTap (ProseMirror-based), JSON doc tree storage, slash commands
- **Styling:** Tailwind CSS v4 with warm muted color palette
- **Navigation:** Labeled sidebar with icons (desktop ≥768px), bottom bar (mobile)

## Modules

| Module | Backend Resource | DynamoDB Tables | Frontend Page |
|---|---|---|---|
| Auth | AuthResource | — (SSM) | LoginPage |
| Inbox | InboxResource | inbox_items | InboxPage |
| Projects | ProjectResource | projects | ProjectsPage, ProjectDetailPage |
| Tasks | TaskResource | tasks | (within ProjectDetailPage) |
| Lists | ListResource | lists, list_items | ListsPage |
| Pages | PageResource | pages, page_content | PagesPage |
| Habits | HabitResource | habits, habit_entries | HabitsPage |
| Memorize | DeckResource | decks, cards | MemorizePage |
| Books | BookResource | books | BooksPage |
| Calendar | CalendarResource | calendar_events | CalendarPage |
| Images | ImageResource | — (S3 / filesystem) | (within PageEditor) |

## Key Patterns

- **Single user:** hardcoded `userId = "default"` everywhere. No user management.
- **ULIDs** for all entity IDs (sortable, unique).
- **Cascade deletes:** project delete cascades to tasks → list_items → pages. Page delete cascades to children recursively. Book delete cascades to page tree. Deck delete cascades to cards. Habit delete cascades to entries.
- **Dev mode:** DynamoDB Local (standalone JAR), filesystem for images, hardcoded auth, real Google Books API, auto-seeded data.
- **FSRS:** spaced repetition for Memorize cards via `io.github.open-spaced-repetition:fsrs` library. Cards are reviewed through `Scheduler.reviewCard()`, state persisted as JSON-serializable fields in DynamoDB.
- **Streak logic:** YES extends, NO breaks, SKIP is neutral.
- **Rich text:** TipTap editor with slash commands, page linking, image upload. Used in Pages, Project notes, Book notes, and Memorize card front/back.
- **Calendar sidebar:** Today tasks panel + Habits panel, date-aware (follows daily view navigation).

## Frontend Components

- **PageEditor** — full TipTap editor with grouped toolbar, slash commands, page link picker, image upload
- **MiniEditor** — compact TipTap editor for flashcard front/back
- **SlashMenu** — type `/` to insert blocks (headings, lists, code, table, quote, divider)
- **PageLinkPicker** — search/create pages to link within the editor
- **ConvertPicker** — modal for moving inbox items to projects or lists
- **Nav** — labeled sidebar (desktop) with logo + logout, bottom bar (mobile)

## Color Palette

| Variable | Value | Usage |
|---|---|---|
| `--bg` | `#FAF7F2` | Page background (warm cream) |
| `--accent-sage` | `#8B9E7C` | Primary accent |
| `--accent-terracotta` | `#C4836A` | Secondary accent |
| `--accent-blue` | `#7B9EB2` | Links, calendar events |
| `--danger` | `#C47070` | Destructive actions |
