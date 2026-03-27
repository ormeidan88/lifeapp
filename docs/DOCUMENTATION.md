# LifeApp Documentation

## Table of Contents

1. [Product Overview](#product-overview)
2. [Modules](#modules)
3. [Architecture](#architecture)
4. [Tech Stack](#tech-stack)
5. [Data Model](#data-model)
6. [API Reference](#api-reference)
7. [Frontend Structure](#frontend-structure)
8. [Authentication](#authentication)
9. [Local Development](#local-development)
10. [Production Deployment](#production-deployment)
11. [Testing](#testing)
12. [Adding New Features](#adding-new-features)

---

## Product Overview

LifeApp is a personal productivity application designed around two principles:

- **One place for everything.** Tasks, notes, habits, flashcards, book notes, and calendar — all in a single app instead of scattered across Todoist, Notion, Anki, Google Calendar, etc.
- **Calm by design.** Inspired by Cal Newport's slow productivity philosophy. No gamification, no badges, no streaks with fire emojis. Warm muted colors, generous whitespace, minimal chrome.

It's a single-user application. There are no accounts, no sharing, no collaboration features. One person, one life, one app.

### Design Philosophy

- **GTD (Getting Things Done)** workflow: capture everything in the Inbox, process into Projects/Lists, review regularly
- **Reduce overhead**: every interaction should be fast. Keyboard shortcuts, auto-focus, slash commands in the editor
- **No clutter**: each view shows one thing well. No dashboards with 15 widgets

---

## Modules

### 📥 Inbox

The GTD capture bucket. Anything that comes to mind goes here first.

- Auto-focuses the input on navigation — start typing immediately
- Items show relative timestamps ("3m ago", "2h ago")
- **Move →** button on each item opens a picker to send it to a Project (as a task) or a List
- Can also create a new project directly from the picker

### 📋 Projects

Projects are containers for related tasks and notes. Each project has:

- **Status lifecycle**: Not Started → In Progress → Done / Cancelled
- **Tasks tab**: ordered task list with drag-and-drop reordering, nested subtasks, checkboxes
- **Notes tab**: full rich-text editor (same as Pages) for project documentation
- A colored left border on the card indicates status at a glance

When a project is deleted, everything cascades: tasks, list references, and the project's page tree.

### 📌 Lists

GTD-style lists for organizing tasks across projects.

- **Today**: what you're working on right now. Tasks persist until done or moved.
- **Someday**: ideas you don't want to forget but aren't acting on now.
- **Custom lists**: create your own (e.g., "Waiting For", "Next Actions")
- System lists (Today, Someday) cannot be deleted
- Add tasks directly from the list view — they're created in your first active project
- Two-panel layout: list sidebar on the left, task list on the right

### 📝 Pages

Standalone rich-text documents, like Notion pages.

- **TipTap editor** with: headings (H1-H3), bold, italic, bullet/numbered/task lists, code blocks, tables, blockquotes, images, links, page links, horizontal rules
- **Slash commands**: type `/` to get a dropdown menu of block types
- **Page linking**: click 📄 in the toolbar to search for and link to other pages. Creates new pages if no match.
- **Unlimited nesting**: pages can contain child pages, navigable via links below the title
- **Inline rename**: click the page title to edit it
- Pages are shared infrastructure — Projects and Books also use the same page/editor system

### ✅ Habits

Daily habit tracking with a visual streak grid.

- **7-day view** centered on today (today ± 3 days)
- **← → arrows** to navigate to past/future weeks
- **"Back to today"** link when navigated away
- **Click to cycle**: each cell cycles through Yes (✓ green) → No (✗ red) → Skip (– gray) → empty
- **Streak calculation**: consecutive YES days. SKIP is neutral (doesn't break streak). NO breaks it.
- Color legend at the top (Done / Missed / Skipped)
- Table-style layout with habit names on the left, day columns with weekday + date headers
- Delete habits with the ✕ button on hover
- Also accessible from the Calendar sidebar panel for quick daily logging

### 🧠 Memorize

Spaced repetition flashcards using the FSRS algorithm (same algorithm Anki uses).

- **Decks** contain cards. Each deck shows card count and due count.
- **Cards** have rich-text front and back using MiniEditor (supports bold, italic, lists, code blocks — same capabilities as the page editor)
- **Review mode**: shows due cards one at a time. Click to flip. Rate: Again (red) / Hard (orange) / Good (green) / Easy (blue). Progress bar shows position. FSRS computes the next review date.
- **Manage mode**: add new cards with rich-text editor, view all existing cards in a two-column preview
- **API key access**: cards can be created programmatically via `X-API-Key` header for bulk import
- "All caught up" message when no cards are due, auto-opens manage mode

### 📚 Books

Book notes with cover art from Google Books.

- **Search as you type**: real-time search against Google Books API with 400ms debounce
- **Cover images**: extracted from Google Books (HTTPS, largest available), with Open Library ISBN fallback
- Each book auto-creates a notes page (same rich-text editor)
- Duplicate detection: can't add the same book twice (matched by Google Books ID)
- Delete cascades to the notes page

### 📅 Calendar

Weekly/daily time blocking with drag-and-drop.

- **Two views**: Day and Week, toggled in the header
- **6:00 AM – 10:00 PM** time range
- **Click a cell** to create an event with auto-filled date and time
- **Drag events** to reschedule (drop on a different cell)
- **Delete events** via ✕ button on hover
- **Current time indicator**: red line + dot showing the current time
- **Today column** highlighted with sage green background
- **Today tasks panel** on the right showing tasks from the Today list with checkboxes
- **Habits panel** below the Today panel showing all habits for the selected date with ✓/✗/– buttons. In weekly view shows today's habits; in daily view follows the viewed date.
- **External integration**: `POST /api/calendar/events` with API key for pushing events from Outlook or other sources

---

## Architecture

```
┌─────────────────────────────────────────────────────┐
│                    Browser (SPA)                     │
│  React 19 + TypeScript + TipTap + Tailwind CSS v4   │
│  Vite dev server (dev) / static files (prod)        │
└──────────────────────┬──────────────────────────────┘
                       │ HTTP (JSON)
┌──────────────────────▼──────────────────────────────┐
│                  Quarkus Backend                     │
│  Java 21 + RESTEasy Reactive + Jackson              │
│  Session cookie + API key authentication            │
├─────────────────────────────────────────────────────┤
│  AuthResource    InboxResource    ProjectResource    │
│  TaskResource    ListResource     PageResource       │
│  HabitResource   DeckResource     BookResource       │
│  CalendarResource ImageResource                      │
└──────┬──────────────┬───────────────┬───────────────┘
       │              │               │
  ┌────▼────┐   ┌────▼────┐   ┌─────▼─────┐
  │ DynamoDB │   │   S3    │   │  Google   │
  │ 13 tables│   │ images  │   │ Books API │
  └─────────┘   └─────────┘   └───────────┘
```

### Key Design Decisions

- **Monolith**: one fat jar, one EC2 instance. No microservices. Simplicity over scalability (single user).
- **DynamoDB multi-table**: one table per entity type. Simple to reason about, no single-table design complexity.
- **ULIDs** for all IDs: sortable by creation time, globally unique, no coordination needed.
- **TipTap (ProseMirror)** for rich text: JSON document tree stored in DynamoDB. Supports undo/redo client-side.
- **FSRS** for spaced repetition: science-backed algorithm via `io.github.open-spaced-repetition:fsrs` Java library.
- **No user management**: hardcoded `userId = "default"` everywhere. Auth is just a password gate.

---

## Tech Stack

### Backend

| Component | Technology |
|---|---|
| Language | Java 21 |
| Framework | Quarkus 3.17 |
| REST | RESTEasy Reactive |
| JSON | Jackson |
| Database | AWS DynamoDB (enhanced client) |
| Secrets | AWS SSM Parameter Store |
| Images | AWS S3 (prod) / filesystem (dev) |
| Password hashing | BCrypt (favre lib) |
| IDs | ULID (f4b6a3 lib) |
| Spaced repetition | FSRS (open-spaced-repetition lib) |
| Build | Maven with wrapper |
| Packaging | Uber JAR |

### Frontend

| Component | Technology |
|---|---|
| Framework | React 19 + TypeScript |
| Build | Vite |
| Styling | Tailwind CSS v4 |
| Rich text editor | TipTap (ProseMirror-based) |
| Routing | State-based (no react-router in use) |
| Testing | Vitest + Testing Library |

---

## Data Model

13 DynamoDB tables, all using on-demand (PAY_PER_REQUEST) capacity:

| Table | PK | SK | Purpose |
|---|---|---|---|
| `inbox_items` | userId | itemId (ULID) | Quick capture items |
| `projects` | userId | projectId (ULID) | Project metadata + status |
| `tasks` | projectId | taskId (ULID) | Tasks within projects. Has `parentTaskId` for nesting, `position` for ordering |
| `lists` | userId | listId (ULID) | GTD lists (Today, Someday, custom) |
| `list_items` | listId | taskId | Task-to-list assignments |
| `pages` | userId | pageId (ULID) | Page metadata. `ownerType` (standalone/project/book), `parentPageId` for nesting |
| `page_content` | pageId | version (timestamp) | TipTap JSON content. Only latest version kept. |
| `habits` | userId | habitId (ULID) | Habit definitions |
| `habit_entries` | habitId | date (YYYY-MM-DD) | Daily yes/no/skip entries |
| `decks` | userId | deckId (ULID) | Flashcard decks |
| `cards` | deckId | cardId (ULID) | Cards with FSRS state (stability, difficulty, due, state, reps, lapses) |
| `books` | userId | bookId (ULID) | Book metadata + Google Books ID + cover URL |
| `calendar_events` | userId | eventId (ULID) | Time blocks with date, startTime, endTime, color, source |

### Cascade Delete Rules

- **Project** → tasks → list_items referencing those tasks → project page → child pages → page content
- **Page** → child pages (recursive) → page content for each
- **Book** → associated page → child pages → page content
- **Deck** → all cards
- **Habit** → all entries
- **List** (custom only) → all list_items

---

## API Reference

Base URL: `/api`. All endpoints except `/api/auth/login` and `/api/auth/check` require authentication (session cookie or `X-API-Key` header).

### Auth
| Method | Path | Description |
|---|---|---|
| POST | `/api/auth/login` | Login with `{ "password": "..." }`. Sets session cookie. |
| POST | `/api/auth/logout` | Clears session. |
| GET | `/api/auth/check` | Returns `{ "authenticated": true/false }`. |

### Inbox
| Method | Path | Description |
|---|---|---|
| GET | `/api/inbox` | List items (sorted by createdAt desc) |
| POST | `/api/inbox` | Create item `{ "text": "..." }` |
| DELETE | `/api/inbox/{id}` | Delete item |
| POST | `/api/inbox/{id}/convert` | Convert to task/project/list-item |

### Projects
| Method | Path | Description |
|---|---|---|
| GET | `/api/projects` | List all (optional `?status=IN_PROGRESS`) |
| POST | `/api/projects` | Create `{ "name": "..." }`. Auto-creates page. |
| GET | `/api/projects/{id}` | Get details |
| PATCH | `/api/projects/{id}` | Update name/status |
| DELETE | `/api/projects/{id}` | Delete with cascade |

### Tasks
| Method | Path | Description |
|---|---|---|
| GET | `/api/tasks?projectId={id}` | List tasks for project (sorted by position) |
| POST | `/api/tasks` | Create `{ "projectId", "title", "parentTaskId"? }` |
| PATCH | `/api/tasks/{id}?projectId={pid}` | Update title/done/position |
| POST | `/api/tasks/reorder` | Reorder `{ "projectId", "taskIds": [...] }` |
| DELETE | `/api/tasks/{id}?projectId={pid}` | Delete with subtask cascade |

### Lists
| Method | Path | Description |
|---|---|---|
| GET | `/api/lists` | List all (lazy-creates Today/Someday) |
| POST | `/api/lists` | Create custom list |
| POST | `/api/lists/{id}/items` | Add task `{ "taskId": "..." }` |
| GET | `/api/lists/{id}/items` | Get items with full task objects |
| DELETE | `/api/lists/{id}/items/{taskId}` | Remove item |
| DELETE | `/api/lists/{id}` | Delete list (403 for system lists) |

### Pages
| Method | Path | Description |
|---|---|---|
| GET | `/api/pages?ownerType=standalone` | List pages (filter by owner/parent) |
| GET | `/api/pages/search?q={query}` | Search pages by title |
| POST | `/api/pages` | Create page |
| GET | `/api/pages/{id}` | Get page with content |
| PATCH | `/api/pages/{id}` | Update title/content |
| DELETE | `/api/pages/{id}` | Delete with child cascade |

### Habits
| Method | Path | Description |
|---|---|---|
| GET | `/api/habits` | List with streak calculations |
| POST | `/api/habits` | Create `{ "name", "color"? }` |
| PATCH | `/api/habits/{id}` | Update |
| DELETE | `/api/habits/{id}` | Delete with entries |
| POST | `/api/habits/{id}/entries` | Upsert entry `{ "date", "value": "YES\|NO\|SKIP" }` |
| GET | `/api/habits/{id}/entries?from=&to=` | Get entries by date range |

### Decks & Cards
| Method | Path | Description |
|---|---|---|
| GET | `/api/decks` | List decks with card/due counts |
| POST | `/api/decks` | Create deck |
| DELETE | `/api/decks/{id}` | Delete with cards |
| POST | `/api/decks/{id}/cards` | Create card (API key accessible) |
| GET | `/api/decks/{id}/cards` | List all cards |
| GET | `/api/decks/{id}/review` | Get due cards (max 50) |
| POST | `/api/decks/{id}/cards/{cid}/review` | Review `{ "rating": "AGAIN\|HARD\|GOOD\|EASY" }` |

### Books
| Method | Path | Description |
|---|---|---|
| GET | `/api/books/search?q={query}` | Search Google Books API |
| GET | `/api/books` | List library |
| POST | `/api/books` | Add book (409 if duplicate) |
| DELETE | `/api/books/{id}` | Delete with page cascade |

### Calendar
| Method | Path | Description |
|---|---|---|
| GET | `/api/calendar/events?from=&to=` | List events in range |
| POST | `/api/calendar/events` | Create event (API key accessible) |
| PATCH | `/api/calendar/events/{id}` | Update |
| DELETE | `/api/calendar/events/{id}` | Delete |

### Images
| Method | Path | Description |
|---|---|---|
| POST | `/api/images` | Upload image (multipart, max 10MB, JPEG/PNG/GIF/WebP) |

---

## Frontend Structure

```
frontend/src/
├── api/
│   └── client.ts              # API client — all backend calls
├── components/
│   ├── layout/
│   │   └── Nav.tsx             # Labeled sidebar (desktop) + BottomNav (mobile)
│   ├── editor/
│   │   ├── PageEditor.tsx      # Full TipTap editor with grouped toolbar + slash menu
│   │   ├── MiniEditor.tsx      # Compact editor for flashcard front/back
│   │   ├── SlashMenu.tsx       # Slash command hook (type / to insert blocks)
│   │   ├── PageLinkExtension.tsx  # Custom TipTap node for page links
│   │   └── PageLinkPicker.tsx  # Search/create page picker modal
│   └── ConvertPicker.tsx       # Inbox → Project/List conversion modal
├── hooks/
│   └── useAuth.ts              # Authentication state management
├── pages/
│   ├── LoginPage.tsx           # Password login with branding
│   ├── InboxPage.tsx           # GTD capture with move-to picker
│   ├── ProjectsPage.tsx        # Project cards with status borders
│   ├── ProjectDetailPage.tsx   # Tasks (drag-and-drop) + Notes tabs
│   ├── ListsPage.tsx           # Two-panel list view with inline task creation
│   ├── PagesPage.tsx           # Page cards, child pages, inline rename
│   ├── HabitsPage.tsx          # 7-day grid with week navigation
│   ├── MemorizePage.tsx        # Deck list, review mode, manage mode
│   ├── BooksPage.tsx           # Book library with real-time search
│   └── CalendarPage.tsx        # Week/day view + Today tasks + Habits panel
├── test/                       # Vitest tests
├── App.tsx                     # Root component, routing, layout
├── main.tsx                    # Entry point
└── index.css                   # Global styles, TipTap styles, CSS variables
```

### Color Palette (CSS Variables)

| Variable | Value | Usage |
|---|---|---|
| `--bg` | `#FAF7F2` | Page background (warm cream) |
| `--text` | `#3D3D3D` | Primary text |
| `--text-muted` | `#8A8A8A` | Secondary text |
| `--border` | `#E8E4DE` | Borders, dividers |
| `--accent-sage` | `#8B9E7C` | Primary accent (buttons, active states) |
| `--accent-terracotta` | `#C4836A` | Secondary accent (current time indicator) |
| `--accent-blue` | `#7B9EB2` | Tertiary accent (links, calendar events) |
| `--accent-sand` | `#D4C5A9` | Subtle accent |
| `--danger` | `#C47070` | Destructive actions |

---

## Authentication

### How It Works

1. Password is stored as a BCrypt hash in AWS SSM Parameter Store (`/lifeapp/auth/password-hash`)
2. User submits password → backend verifies with BCrypt → sets an HttpOnly session cookie (Secure flag in prod)
3. All subsequent requests include the cookie automatically
4. Sessions are stored in-memory (ConcurrentHashMap) — they don't survive server restarts
5. API key (stored in SSM at `/lifeapp/auth/api-key`) can be used instead of cookie via `X-API-Key` header

### Dev Mode

- Password: `dev`
- API key: `dev-api-key`
- Both configured in `application.properties` under `%dev.` prefix
- SSM is not used in dev mode

---

## Local Development

### Prerequisites

- Java 21+
- Node.js 20+
- DynamoDB Local ([download](https://docs.aws.amazon.com/amazondynamodb/latest/developerguide/DynamoDBLocal.DownloadingAndRunning.html))

### Setup

```bash
# 1. Start DynamoDB Local
cd ~/dynamodb-local
java -Djava.library.path=./DynamoDBLocal_lib -jar DynamoDBLocal.jar -sharedDb -port 8001

# 2. Start backend (new terminal)
cd ~/Work/lifeapp/lifeapp
./mvnw quarkus:dev
# Or build and run the jar:
./mvnw package -DskipTests
java -Dquarkus.profile=dev -jar target/lifeapp-1.0.0-runner.jar

# 3. Start frontend (new terminal)
cd ~/Work/lifeapp/lifeapp/frontend
npm install
npx vite
```

Open http://localhost:5173, login with password `dev`.

### What Happens on First Start

1. `TableInitializer` auto-creates all 13 DynamoDB tables
2. `SeedData` populates every module with realistic mock data:
   - 5 inbox items
   - 3 projects with tasks
   - Today/Someday lists with items
   - 3 standalone pages (one with child page)
   - 3 habits with 30 days of entries
   - 2 flashcard decks with 10 cards each
   - 3 books with covers and notes
   - 10 calendar events across the current week

### Resetting Data

Kill DynamoDB Local and restart it — all data is in-memory with `-sharedDb` flag.

---

## Production Deployment

### Build

```bash
./build-prod.sh
# This:
# 1. Builds the React frontend
# 2. Copies dist/ to src/main/resources/META-INF/resources/
# 3. Packages the uber JAR
```

### Deploy

```bash
# 1. Create DynamoDB tables (one-time)
# Use AWS CLI or CloudFormation — see data model section for table schemas

# 2. Store secrets in SSM
aws ssm put-parameter --name /lifeapp/auth/password-hash --value "$(htpasswd -nbBC 10 '' 'YOUR_PASSWORD' | cut -d: -f2)" --type SecureString
aws ssm put-parameter --name /lifeapp/auth/api-key --value "YOUR_API_KEY" --type SecureString

# 3. Create S3 bucket for images
aws s3 mb s3://lifeapp-images

# 4. Upload and run
aws s3 cp target/lifeapp-1.0.0-runner.jar s3://your-deploy-bucket/
# On EC2:
java -jar lifeapp-1.0.0-runner.jar
```

### Infrastructure

- Route53 → ALB → EC2 (Ubuntu) — already set up
- DynamoDB tables: on-demand capacity (PAY_PER_REQUEST)
- S3 bucket: for image uploads
- SSM Parameter Store: password hash + API key
- Systemd service recommended for auto-restart

---

## Testing

### Backend (118 tests, 97.3% line coverage)

```bash
cd ~/Work/lifeapp/lifeapp
./mvnw test
# Coverage report: target/site/jacoco/index.html
```

Test structure:
- `app.auth` — AuthResourceTest (12), SessionManagerTest (6)
- `app.resource` — one test class per resource + CoverageBoostTest + CoverageBoost2Test
- `app.dynamo` — TableInitializerTest, SeedDataTest, TablesTest
- `app.config` — AuthConfigTest

All resource tests are integration tests using `@QuarkusTest` with a real DynamoDB Local instance.

### Frontend (67 tests, 99% line coverage)

```bash
cd ~/Work/lifeapp/lifeapp/frontend
npx vitest run --coverage
```

Test structure:
- `api.test.ts` + `api-full.test.ts` — API client (all endpoints)
- `useAuth.test.ts` — auth hook
- `LoginPage.test.tsx` — login form
- `Nav.test.tsx` — sidebar + bottom nav

---

## Adding New Features

### Adding a New Module (e.g., "Finance")

**Backend:**

1. Create model: `src/main/java/app/model/Transaction.java` — DynamoDB bean with `@DynamoDbPartitionKey`/`@DynamoDbSortKey`
2. Add table to `Tables.java` — new `@Produces @Named("transactions")` method
3. Add table creation to `TableInitializer.java` — new `createIfMissing(...)` call
4. Create resource: `src/main/java/app/resource/TransactionResource.java` — REST endpoints with `@Path("/api/transactions")`
5. Add seed data to `SeedData.java` if needed
6. Write tests in `src/test/java/app/resource/TransactionResourceTest.java`

**Frontend:**

1. Create page: `src/pages/FinancePage.tsx`
2. Add to `Nav.tsx` — add entry to `icons` and `items` arrays
3. Add to `App.tsx` — import + add case to `renderPage()` switch
4. Add API methods to `src/api/client.ts`
5. Write tests

### Adding a Field to an Existing Model

DynamoDB is schemaless — just add the field to the Java bean class and it works. No migration needed. Existing items will have `null` for the new field.

### Adding a New Editor Feature

1. Install the TipTap extension: `npm install @tiptap/extension-xyz`
2. Add it to the `extensions` array in `PageEditor.tsx` (and `MiniEditor.tsx` if needed)
3. Add a toolbar button in the toolbar section of `PageEditor.tsx`
4. Add CSS styles in `index.css` under the `.tiptap` selector

### Adding a New Slash Command

Edit `SlashMenu.tsx` — add an entry to the `slashItems` array:

```typescript
{ title: 'Callout', icon: '💡', description: 'Highlighted block', command: (e) => e.chain().focus()... }
```

### Environment Configuration

All config is in `src/main/resources/application.properties`. Dev overrides use `%dev.` prefix. Test overrides are in `src/test/resources/application.properties`.

Key properties:
- `quarkus.dynamodb.endpoint-override` — DynamoDB endpoint (localhost:8001 in dev)
- `app.auth.dev-mode` — enables dev auth (bypasses SSM)
- `app.images.storage` — `filesystem` (dev) or `s3` (prod)
- `app.books.google-api-key` — optional Google Books API key for higher rate limits
