# Spec: Recurring Calendar Events

**Status:** Draft
**Research:** [docs/research/recurring-events.md](../docs/research/recurring-events.md)

## Summary

Add recurring event support to the calendar. Users create a series with a recurrence rule (daily, weekly, monthly, custom interval). Occurrences are generated on the fly at query time. Individual occurrences can be edited or deleted as exceptions.

## Data Model Changes

### Modified: `CalendarEvent`

Add three fields:

| Field | Type | Description |
|---|---|---|
| `recurrenceRule` | String | JSON string: `{"freq":"WEEKLY","interval":1,"daysOfWeek":["MON","WED"],"monthlyType":"DATE","endDate":"2026-12-31"}`. Null for one-off events. |
| `recurrenceId` | String | For exceptions: eventId of the parent series. Null for series roots and one-off events. |
| `recurrenceDate` | String | For exceptions: the original occurrence date this record replaces (ISO date). |

**Recurrence rule JSON schema:**
```json
{
  "freq": "DAILY | WEEKLY | MONTHLY | CUSTOM",
  "interval": 1,
  "unit": "DAY | WEEK | MONTH",
  "daysOfWeek": ["MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN"],
  "monthlyType": "DATE | WEEKDAY",
  "endDate": "2026-12-31"
}
```
- `freq`: Required. The recurrence type.
- `interval`: Optional, defaults to 1. For CUSTOM freq, combined with `unit`.
- `unit`: Required for CUSTOM. One of DAY, WEEK, MONTH.
- `daysOfWeek`: Required for WEEKLY. Array of day abbreviations.
- `monthlyType`: Required for MONTHLY. DATE = same day-of-month. WEEKDAY = same weekday position (e.g. 3rd Tuesday).
- `endDate`: Optional. Series runs forever if omitted.

**Event types by field combination:**
| recurrenceRule | recurrenceId | Type |
|---|---|---|
| non-null | null | Recurring series root |
| null | non-null | Exception (edit or delete of single occurrence) |
| null | null | One-off event |

Exception events that represent a deletion have `title` set to `"__DELETED__"`.

## Backend Changes

### New File: `src/main/java/app/service/RecurrenceExpander.java`

A stateless utility class with method:

```java
public static List<String> expand(String recurrenceRuleJson, String seriesStartDate, String rangeFrom, String rangeTo)
```

Returns a list of ISO date strings where the event occurs within `[rangeFrom, rangeTo]`.

**Expansion logic:**
- **DAILY:** Starting from `seriesStartDate`, step by `interval` days. Include dates in range. Stop at `endDate` or `rangeTo`.
- **WEEKLY:** Starting from `seriesStartDate`, for each week (stepping by `interval` weeks), include dates that match `daysOfWeek`. The week anchor is the series start date's week.
- **MONTHLY (DATE):** Starting from `seriesStartDate`, step by `interval` months, same day-of-month. If the day doesn't exist in a month (e.g. 31st in February), skip that month.
- **MONTHLY (WEEKDAY):** Compute the weekday position of the start date (e.g. 3rd Tuesday). Step by `interval` months, find the same weekday position in each month.
- **CUSTOM:** Use `unit` + `interval` to determine step size (N days, N weeks, or N months), then apply the corresponding logic above.

### Modified: `src/main/java/app/resource/CalendarResource.java`

**POST /api/calendar/events (create):**
- Accept optional `recurrenceRule` in the request body. Store it on the event.
- The `date` field becomes the series start date for recurring events.

**GET /api/calendar/events?from=&to= (list):**
- After fetching all events for the user, process them in three groups:
  1. **One-off events** (no recurrenceRule, no recurrenceId): filter by date range as before.
  2. **Recurring series** (has recurrenceRule): call `RecurrenceExpander.expand()` to get occurrence dates in range. For each date, produce a virtual event with the series data + that date. Mark each with `isRecurring: true` and `seriesId` in the response.
  3. **Exceptions** (has recurrenceId): index by `recurrenceId + recurrenceDate`.
- Merge: for each expanded occurrence, check exceptions. If deleted → skip. If modified → use exception's fields. Otherwise → use series fields.
- Response events include: `id` (series eventId for recurring, exception eventId for exceptions), `seriesId` (for recurring occurrences), `isRecurring` (boolean), `recurrenceRule` (for series root occurrences), plus existing fields.

**PATCH /api/calendar/events/{id} (update):**
- New optional body fields: `editMode` ("single" or "all"), `occurrenceDate` (the date of the occurrence being edited).
- **editMode = "all":** Update the series record directly. Set `date` to `occurrenceDate` (truncate past occurrences). Update title/startTime/endTime/color as provided. Delete any exception events with `recurrenceDate >= occurrenceDate`.
- **editMode = "single":** Create a new exception CalendarEvent with `recurrenceId` = series ID, `recurrenceDate` = occurrenceDate, and the modified fields copied from the series + overrides from the request body.
- For non-recurring events: update as before (no change).

**DELETE /api/calendar/events/{id}:**
- New optional query param: `deleteMode` ("single" or "all"), `occurrenceDate`.
- **deleteMode = "all":** Delete the series record and all exception records with matching `recurrenceId`.
- **deleteMode = "single":** Create an exception CalendarEvent with `recurrenceId` = series ID, `recurrenceDate` = occurrenceDate, `title` = "__DELETED__".
- For non-recurring events: delete as before.

### Modified: `src/main/java/app/resource/ConverseResource.java`

Update `add_calendar_event` tool to accept optional `recurrenceRule` parameter (JSON string).

## Frontend Changes

### New File: `frontend/src/components/calendar/EventFormModal.tsx`

A modal form for creating/editing events with fields:
- **Title** (text input, required)
- **Date** (date input, required)
- **Start time** / **End time** (time inputs)
- **Color** (color picker, optional)
- **Repeat** section:
  - Toggle/dropdown: None | Daily | Weekly | Monthly | Custom
  - **Weekly:** Day chips (Mon–Sun), toggleable, multi-select. Pre-select the day matching the chosen date.
  - **Monthly:** Radio/dropdown: "On day N" or "On the Nth [weekday]" (computed from the chosen date).
  - **Custom:** Number input + unit dropdown (days/weeks/months).
  - **End:** Radio: "Never" or "Until [date picker]".
- **Save** and **Cancel** buttons.

Opened by:
- The new "Add event" button in the calendar header (empty form).
- Clicking a time cell (pre-fills date + start time).
- Clicking an existing event (pre-fills all fields for editing).

### New File: `frontend/src/components/calendar/RecurrencePrompt.tsx`

A small modal/popover that asks "This event" or "All events" when editing, deleting, or dragging a recurring occurrence. Returns the user's choice.

### Modified: `frontend/src/pages/CalendarPage.tsx`

- Add "➕ Add event" button in the calendar header bar.
- Replace the inline creation form with `EventFormModal`.
- When clicking an existing event: if recurring, open `EventFormModal` in edit mode (instead of the notes-only modal).
- When saving edits to a recurring event: show `RecurrencePrompt`, then call PATCH with the appropriate `editMode`.
- When deleting a recurring event: show `RecurrencePrompt`, then call DELETE with the appropriate `deleteMode`.
- When dragging a recurring event: on drop, show `RecurrencePrompt`, then call PATCH with the appropriate `editMode`.
- Render 🔁 icon on events where `isRecurring` is true.
- Non-recurring events continue to work exactly as before.

### Modified: `frontend/src/api/client.ts`

Update `calendar.update` and `calendar.delete` to accept additional parameters:
```typescript
update: (id: string, body: any) => request(`/calendar/events/${id}`, { method: 'PATCH', body: JSON.stringify(body) }),
delete: (id: string, params?: { deleteMode?: string; occurrenceDate?: string }) => {
  const q = params ? '?' + new URLSearchParams(params as any).toString() : ''
  return request(`/calendar/events/${id}${q}`, { method: 'DELETE' })
},
```

## Acceptance Criteria

1. **Create recurring event:** User opens the event form modal, fills in details, selects a recurrence pattern, and saves. The event appears on all matching dates in the calendar.
2. **Daily recurrence:** An event set to repeat daily appears every day (or every N days) from the start date.
3. **Weekly recurrence:** An event set to repeat weekly on Mon/Wed/Fri appears on those days each week.
4. **Monthly recurrence (date):** An event set to repeat monthly on the 15th appears on the 15th of each month.
5. **Monthly recurrence (weekday):** An event set to repeat on the "3rd Tuesday" appears on the 3rd Tuesday of each month.
6. **Custom interval:** An event set to repeat every 2 weeks appears biweekly.
7. **End date:** A recurring event with an end date stops appearing after that date.
8. **Forever:** A recurring event with no end date appears indefinitely.
9. **Edit single occurrence:** Editing one occurrence and choosing "This event" only changes that date. Other occurrences remain unchanged.
10. **Edit all occurrences:** Editing and choosing "All events" updates the series from the current date forward.
11. **Delete single occurrence:** Deleting one occurrence and choosing "This event" removes only that date.
12. **Delete all occurrences:** Deleting and choosing "All events" removes the entire series.
13. **Drag single occurrence:** Dragging a recurring event and choosing "This event" moves only that occurrence.
14. **Drag all occurrences:** Dragging and choosing "All events" updates the series time.
15. **Visual indicator:** Recurring events show a 🔁 icon.
16. **Add event button:** A button in the calendar header opens the event form modal.
17. **Cell click still works:** Clicking a time cell opens the form with date/time pre-filled.
18. **Non-recurring events unchanged:** One-off events continue to work exactly as before.
19. **AI tool:** The `add_calendar_event` tool accepts an optional `recurrenceRule` parameter.
