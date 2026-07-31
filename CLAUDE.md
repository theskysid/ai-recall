# CLAUDE.md

**Echo Messaging** — a real-time chat app: Spring Boot (Java 21) backend +
React (Vite) frontend, connected over WebSocket (STOMP/SockJS), PostgreSQL 16.

## Read before making changes

- [ai/project.md](ai/project.md) — what the app is, features today, planned next
- [ai/architecture.md](ai/architecture.md) — stack, layout, how the tiers connect, layering
- [ai/conventions.md](ai/conventions.md) — naming, error handling, testing

Read these before making changes.

## Build / run / test

Full stack (local, builds from source):

```bash
docker compose -f docker-compose.local.yml up --build
```

Backend (`echo-backend/`):

```bash
./mvnw clean package    # build (runs tests)
./mvnw test             # tests only
./mvnw spring-boot:run  # run locally
```

Frontend (`echo-frontend/`):

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
  unless a prompt explicitly says to. Auth, friend chat, and global chat are
  stable; extend alongside them.
- Follow existing conventions (see `ai/conventions.md`) rather than
  introducing new patterns, libraries, or architecture.
- DB schema is managed by Hibernate `ddl-auto: update` — new `@Entity`
  classes auto-create tables; there is no migration tool.


## Output Constraints 
 Provide ONLY the requested code, file edits, commands, or factual status. Strip out all conversational filler, pleasantries, and narrations of your thought process. Do not explain what you are about to do. Only provide explanations if a prompt explicitly asks for one.