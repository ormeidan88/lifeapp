# LifeApp

A personal productivity application — one calm place to manage tasks, notes, habits, flashcards, books, and calendar.

## Prerequisites

- Java 21+
- Node.js 20+
- DynamoDB Local ([download](https://docs.aws.amazon.com/amazondynamodb/latest/developerguide/DynamoDBLocal.DownloadingAndRunning.html))

## Run locally

```bash
# 1. Start DynamoDB Local
./start-dynamodb.sh

# 2. Start backend (in a new terminal)
cd ~/Work/lifeapp/lifeapp
./mvnw package -DskipTests
java -Dquarkus.profile=dev -jar target/lifeapp-1.0.0-runner.jar

# 3. Start frontend (in a new terminal)
cd frontend && npm install && npx vite
```

Open http://localhost:5173 and login with password `dev`.

All modules are pre-populated with seed data on first start.

## Modules

| Module | Description |
|---|---|
| 📥 **Inbox** | GTD quick capture with move-to-project/list picker |
| 📋 **Projects** | Project management with tasks (drag-and-drop), status lifecycle, and rich-text notes |
| 📌 **Lists** | GTD lists (Today, Someday, custom) with inline task creation |
| 📝 **Pages** | Standalone rich-text notes with slash commands, page linking, unlimited nesting |
| ✅ **Habits** | Daily tracking with 7-day view, streak calculation, week navigation |
| 🧠 **Memorize** | Spaced repetition flashcards (FSRS) with rich-text front/back |
| 📚 **Books** | Book notes with real-time Google Books search and cover art |
| 📅 **Calendar** | Weekly/daily time blocking with drag-and-drop, Today tasks panel, Habits panel |

## Tech Stack

- **Backend:** Java 21, Quarkus 3.17, DynamoDB, FSRS
- **Frontend:** React 19, TypeScript, TipTap, Tailwind CSS v4, Vite
- **Auth:** BCrypt + session cookie + API key
- **Editor:** TipTap with slash commands, page links, image upload, tables, code blocks

## Testing

```bash
# Backend (118 tests, 97% coverage)
./mvnw test

# Frontend (67 tests, 99% coverage)
cd frontend && npx vitest run --coverage
```

## Documentation

See [docs/DOCUMENTATION.md](docs/DOCUMENTATION.md) for comprehensive product and developer documentation.
