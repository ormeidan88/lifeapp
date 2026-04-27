# Spec: Daily Notes in Calendar

**Status:** Draft
**Research:** [docs/research/daily-notes.md](../docs/research/daily-notes.md)

## Summary

Add a rich-text daily notes panel to the Calendar page's daily view. Each day gets one note document (TipTap JSON) that auto-saves as the user types. A search API lets the AI agent find notes by keyword.

## Data Model

### New DynamoDB Table: `daily_notes`

| Field | Type | Key | Description |
|---|---|---|---|
| `userId` | String | Partition key | Hardcoded `"default"` |
| `date` | String | Sort key | ISO date, e.g. `"2026-04-26"` |
| `content` | String | — | TipTap JSON string |
| `updatedAt` | String | — | ISO instant |

No GSI needed. Direct PK+SK lookup for single-date fetch. Partition scan for range queries and search.

## Backend Changes

### New File: `src/main/java/app/model/DailyNote.java`

```java
@DynamoDbBean
public class DailyNote {
    private String userId;   // PK
    private String date;     // SK — "2026-04-26"
    private String content;  // TipTap JSON
    private String updatedAt;
    // standard getters/setters with @DynamoDbPartitionKey, @DynamoDbSortKey
}
```

### New File: `src/main/java/app/resource/DailyNoteResource.java`

Path: `/api/daily-notes`

| Method | Path | Request | Response | Description |
|---|---|---|---|---|
| `GET` | `/{date}` | — | `{ date, content, updatedAt }` or 404 | Get note for a specific date |
| `PUT` | `/{date}` | `{ content }` | `{ date, content, updatedAt }` | Upsert note. Sets `updatedAt` to now. |
| `GET` | `?from=&to=` | Query params | `{ notes: [{ date, content, updatedAt }] }` | List notes in date range. Query all notes for user, filter by date range in Java. Sort by date descending. |
| `GET` | `/search?q=` | Query param | `{ notes: [{ date, snippet, updatedAt }] }` | Full-text search. Scan all notes, extract plain text from TipTap JSON content, case-insensitive contains match. Return matching notes with `snippet` (first 200 chars of plain text). Sort by date descending. |

Plain text extraction for search: Walk the TipTap JSON tree, collect all `text` field values, join with spaces.

### Modify: `src/main/java/app/dynamo/Tables.java`

Add a new `@Produces @Named("dailyNotes")` method returning `DynamoDbTable<DailyNote>` for table `"daily_notes"`.

### Modify: `src/main/java/app/dynamo/TableInitializer.java`

Add `daily_notes` table creation in `init()` with `userId` (HASH) + `date` (RANGE). No GSI.

### Modify: `src/main/java/app/resource/ConverseResource.java`

Add two AI tools in `buildTools()`:

1. **`get_daily_notes`** — Get notes for a date range.
   - Properties: `from` (start date, required), `to` (end date, required)
   - Calls: `GET /api/daily-notes?from={from}&to={to}`

2. **`search_daily_notes`** — Search notes by keyword.
   - Properties: `query` (search text, required)
   - Calls: `GET /api/daily-notes/search?q={query}`

Add corresponding cases in `executeTool()` switch expression.

## Frontend Changes

### New File: `frontend/src/components/calendar/DailyNotePanel.tsx`

A panel component that:
- Accepts `date` prop (ISO date string)
- Fetches note for the date via `api.dailyNotes.get(date)` on mount / date change
- Renders a TipTap editor (reuse `PageEditor` configuration: StarterKit, Table, Image, Link, TaskList, TaskItem — exclude PageLink extension since it's page-specific)
- Auto-saves via 1000ms debounce calling `api.dailyNotes.put(date, { content })`
- On date change: save any pending content for the previous date, then load the new date's content
- Empty state: show empty editor ready to type (no placeholder button)
- Header: "Notes" label with the date

Editor sizing: The panel should be `w-80` (320px) to give comfortable editing room — wider than the existing `w-56` panels since rich text needs more space.

### Modify: `frontend/src/api/client.ts`

Add `dailyNotes` namespace:

```typescript
dailyNotes: {
  get: (date: string) => request(`/daily-notes/${date}`),
  put: (date: string, body: any) => request(`/daily-notes/${date}`, { method: 'PUT', body: JSON.stringify(body) }),
  list: (from: string, to: string) => request(`/daily-notes?from=${from}&to=${to}`),
  search: (q: string) => request(`/daily-notes/search?q=${encodeURIComponent(q)}`),
}
```

### Modify: `frontend/src/pages/CalendarPage.tsx`

- Import `DailyNotePanel`
- In daily view only: render `<DailyNotePanel date={selectedDate} />` as a third panel in the right-side panel column, below the Habits panel
- Panel order in daily view: Today tasks → Habits → Daily Notes
- In weekly view: do not render the notes panel

## Acceptance Criteria

1. **View note:** Navigating to a day in daily view shows the notes panel with any existing note content loaded in the TipTap editor.
2. **Create note:** Typing in the empty editor for a day that has no note auto-saves after 1s of inactivity, creating the note via PUT.
3. **Edit note:** Editing an existing note auto-saves after 1s of inactivity via PUT.
4. **Date navigation:** Switching days in daily view loads the correct note for the new date. Any pending save for the previous date completes first.
5. **Weekly view:** The notes panel is not visible in weekly view.
6. **Rich text:** The editor supports the same formatting as Pages (bold, italic, headings, lists, task lists, code blocks, tables, images, links, blockquote).
7. **API - get by date:** `GET /api/daily-notes/2026-04-26` returns the note or 404.
8. **API - upsert:** `PUT /api/daily-notes/2026-04-26` with `{ content: "..." }` creates or updates the note.
9. **API - date range:** `GET /api/daily-notes?from=2026-04-01&to=2026-04-30` returns all notes in that range.
10. **API - search:** `GET /api/daily-notes/search?q=meeting` returns notes containing "meeting" with plain-text snippets.
11. **AI tools:** The AI agent can use `get_daily_notes` and `search_daily_notes` tools to query notes.
12. **Auto-save debounce:** Rapid typing does not trigger multiple saves — only one save fires 1s after typing stops.
13. **Empty state:** A day with no note shows an empty editor, not a placeholder or button.
