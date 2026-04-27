# Research: Daily Notes in Calendar

**Date:** 2026-04-26
**Status:** Approved

## Problem

Users want to attach free-form notes to specific calendar days — either as preparation before a day or as a journal/log during and after. Notes should be persistent and navigable by date.

## Decisions

| Decision | Choice | Rationale |
|---|---|---|
| Data model | New `daily_notes` table, separate from `calendar_events` | Daily notes are a distinct entity (one per day, rich text, no time semantics). Follows existing one-table-per-entity pattern. |
| Storage format | TipTap JSON (same as Pages module) | Consistent with existing rich-text storage. Native editor format. |
| Editor | TipTap rich-text editor (reuse Pages editor config) | User wants rich text. Reusing existing TipTap setup minimizes new code. |
| UI placement | New panel in Calendar page, daily view only | Sits alongside Today tasks and Habits panels. Not shown in weekly view. |
| Save behavior | Auto-save with ~1s debounce after typing stops | Matches user expectation of seamless editing. No save button needed. |
| Empty state | Show empty editor ready to type (no "Add note" button) | Minimal friction — just start typing. |
| Search implementation | Full partition scan + Java in-memory text search | Single-user app with bounded data (~365 notes/year). Extract plain text from TipTap JSON server-side for search. No external search infrastructure needed. |
| AI agent API | Expose both date-range fetch and keyword search endpoints | `GET /api/daily-notes?from=&to=` for range queries, `GET /api/daily-notes/search?q=` for text search. |
| Notes per day | One note document per day | Single text area per day, user writes as much as they want. |
| Weekly view | No notes panel | Notes panel only appears in daily view. |

## Architecture

### Data Model

New DynamoDB table `daily_notes`:
- PK: `userId` (String) — hardcoded `"default"`
- SK: `date` (String) — ISO date like `"2026-04-26"`
- `content` (String) — TipTap JSON
- `updatedAt` (String) — ISO instant

No GSI needed. PK+SK gives direct access by date. Partition scan covers search.

### API

| Method | Path | Description |
|---|---|---|
| GET | `/api/daily-notes/{date}` | Get note for a specific date. Returns 404 if none. |
| PUT | `/api/daily-notes/{date}` | Create or update note for a date (upsert). |
| GET | `/api/daily-notes?from=&to=` | List notes in date range. |
| GET | `/api/daily-notes/search?q=` | Full-text search across all notes. |

### Frontend

- New `DailyNotePanel` component in CalendarPage, shown only in daily view
- Reuses TipTap editor configuration from Pages module
- Auto-save with 1s debounce on content change
- Panel width sized for comfortable rich-text editing (~w-80)

## Open Questions

None — all questions resolved during research.
