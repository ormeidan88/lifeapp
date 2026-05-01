# Research: Recurring Calendar Events

**Date:** 2026-04-30
**Status:** Approved

## Problem

Users need recurring events (daily standup, weekly meetings, monthly reviews) without manually creating each occurrence.

## Decisions

| Decision | Choice | Rationale |
|---|---|---|
| Storage strategy | Store rule + generate on the fly | No horizon problem, trivial "edit all", less storage. Single-user scale makes query-time compute negligible. |
| Recurrence patterns | Daily, Weekly (multi-day), Monthly (date or weekday), Custom (every N days/weeks/months) | Aligns with Google Calendar UX. Covers all common patterns. |
| End condition | Forever or until a specific date | Covers the two practical cases. "After N occurrences" omitted for simplicity. |
| Edit behavior | Prompt: "This event" or "All events" | "All" means current + future occurrences. Past occurrences are not retroactively changed. |
| Delete behavior | Prompt: "This event" or "All events" | Same as edit. Single deletion stores an exception. |
| Drag-and-drop | Prompt: "This event" or "All events" | Dragging a recurring occurrence asks before applying. |
| Exceptions | Stored as a map on the series record | `exceptions: { "2026-05-15": { deleted: true }, "2026-05-22": { title: "...", startTime: "..." } }` |
| Event creation | Standalone "Add event" button in header + click-on-cell shortcut | Both open the same form/modal. Cell click pre-fills date/time. |
| Visual indicator | 🔁 icon on recurring event occurrences | Small repeat icon next to title, similar to Google Calendar. |
| Weekly multi-day | Toggleable day chips (Mon–Sun) | Google Calendar pattern. One series can cover multiple days. |
| Monthly options | "Same date" (default) or "Same weekday" (e.g. third Tuesday) | Dropdown choice, matches Google Calendar. |

## Architecture

### Data Model

Add recurrence fields to `CalendarEvent`:

| Field | Type | Description |
|---|---|---|
| `recurrenceRule` | String (JSON) | `{ "freq": "DAILY|WEEKLY|MONTHLY|CUSTOM", "interval": 1, "daysOfWeek": ["MON","WED","FRI"], "monthlyType": "DATE|WEEKDAY", "endDate": "2026-12-31" }`. Null for one-off events. |
| `recurrenceId` | String | For exception events: the eventId of the parent series. Null for series roots and one-off events. |
| `recurrenceDate` | String | For exception events: the original date this exception replaces. |

A recurring series is a single CalendarEvent record where `recurrenceRule` is non-null. The `date` field is the series start date.

Exception events (single-occurrence edits) are separate CalendarEvent records with `recurrenceId` pointing to the parent and `recurrenceDate` indicating which occurrence they replace.

Deleted single occurrences are stored as exception events with a `deleted` flag or similar marker, OR as a list within the recurrence rule. Storing them as exception records is more consistent.

### Query Flow (GET events for date range)

1. Fetch all events for user (existing partition scan)
2. For one-off events (no recurrenceRule, no recurrenceId): filter by date range as before
3. For recurring series (has recurrenceRule): expand the rule into occurrence dates within the requested range
4. For exception events (has recurrenceId): collect them by recurrenceId + recurrenceDate
5. Merge: for each expanded occurrence, check if an exception exists. If deleted exception → skip. If modified exception → use exception data. Otherwise → use series data with the occurrence date.
6. Return merged list sorted by date + startTime

### Recurrence Expansion Logic

Server-side Java method that takes a recurrence rule + series start date + query range and returns a list of dates:

- **DAILY:** Every `interval` days from start date
- **WEEKLY:** Every `interval` weeks, on specified `daysOfWeek`, from start date
- **MONTHLY:** Every `interval` months, on same date or same weekday (e.g. 3rd Tuesday)
- **CUSTOM:** Same as above but with arbitrary interval + unit

All capped by `endDate` if specified, and by the query range.

### Edit/Delete Flows

**Edit single occurrence:**
- Create a new CalendarEvent with `recurrenceId` = parent series ID, `recurrenceDate` = the occurrence date, and the modified fields.

**Edit all (current + future):**
- Update the series record. Set series `date` to the current occurrence date (truncate past). Update title/time/etc.

**Delete single occurrence:**
- Create an exception CalendarEvent with `recurrenceId`, `recurrenceDate`, and a `deleted` flag field.

**Delete all:**
- Delete the series record and all its exception records.

## Open Questions

None — all resolved during research.
