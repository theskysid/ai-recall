# CLAUDE.md

**Recall** (codebase: Echo Messaging) — a real-time chat + AI-memory app:
Spring Boot (Java 21) backend + React (Vite) frontend, over WebSocket
(STOMP/SockJS), PostgreSQL 16 + pgvector. Channels support text chat, LiveKit
video calls, Deepgram transcription, and RAG Q&A over a vector memory
(local MiniLM embeddings + Groq/Llama 3 answer synthesis). REST reference for
the AI endpoints: [backend/RAG_API_DOCS.md](backend/RAG_API_DOCS.md).

## Read before making changes

- [ai/project.md](ai/project.md) — what the app is, features today, what was removed
- [ai/architecture.md](ai/architecture.md) — stack, layout, how the tiers connect, layering
- [ai/conventions.md](ai/conventions.md) — naming, error handling, testing

Read these before making changes.

## Build / run / test

Full stack (local, builds from source):

```bash
docker compose -f docker-compose.local.yml up --build
```

Backend (`backend/`):

```bash
./mvnw clean package    # build (runs tests)
./mvnw test             # tests only
./mvnw spring-boot:run  # run locally
```

Frontend (`frontend/`):

```bash
npm install
npm run dev             # dev server (Vite)
npm run build           # production build
npm run lint            # ESLint
```

Ports: frontend `5173`, backend `8080`, Postgres `5433`.

## House rules

- The project runs in **Docker with `.env` already configured**. Do **not**
  add or remove environment variables without also updating `.env.example`
  and `docker-compose.yml` (and `docker-compose.local.yml`) so the container
  still builds and runs cleanly.
- Keep changes **additive** — do not remove or replace existing functionality
  unless a prompt explicitly says to. Auth, friend chat/DMs, and channels
  (text, calls, memory) are stable; extend alongside them. (Global chat and
  phone/SMS OTP have been removed — do not reintroduce.)
- Follow existing conventions (see `ai/conventions.md`) rather than
  introducing new patterns, libraries, or architecture.
- DB schema is managed by Hibernate `ddl-auto: update` — new `@Entity`
  classes auto-create tables; there is no migration tool. pgvector needs
  `CREATE EXTENSION IF NOT EXISTS vector;` run once (not created by `ddl-auto`).
  A new `NOT NULL` column on a populated table needs a DB default in its
  `columnDefinition`, or `ddl-auto` silently skips it.
- AI features need keys in `.env`: `GROQ_API_KEY` (RAG answers + decisions),
  `LIVEKIT_*` (calls), `DEEPGRAM_API_KEY` (transcription). Embeddings are local
  (MiniLM) and need no key. The backend runtime Docker image must stay glibc-
  based (`eclipse-temurin:21-jre`) — ONNX for MiniLM won't load on alpine.


## Output Constraints 
 Provide ONLY the requested code, file edits, commands, or factual status. Strip out all conversational filler, pleasantries, and narrations of your thought process. Do not explain what you are about to do. Only provide explanations if a prompt explicitly asks for one.