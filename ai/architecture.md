# Architecture

## Tech stack

- **Backend** — Spring Boot 3.3.4, Java 21, Spring Web, Security, Data
  JPA/Hibernate, WebSocket (STOMP), JWT (jjwt 0.12), Spring Mail, Google API
  client.
- **AI / calls** — LiveKit Server SDK for WebRTC tokens; Deepgram batch API via
  `java.net.http.HttpClient`; langchain4j all-MiniLM-L6-v2 (local, in-process
  embeddings, 384-dim); `langchain4j-open-ai` pointed at **Groq** (OpenAI-
  compatible, Llama 3) for decision extraction + RAG answer synthesis; pgvector
  (`com.pgvector:pgvector`) for similarity search.
- **Frontend** — React 19, Vite 7, Axios, `@stomp/stompjs` + `sockjs-client`,
  `react-router-dom` 7, `@react-oauth/google`, `@livekit/components-react`,
  Tailwind CSS 4 (`@tailwindcss/vite`), `motion`, and shadcn-style primitives
  (`@radix-ui/react-slot`, `class-variance-authority`) in `components/ui/`.
- **Database** — PostgreSQL 16 + `vector` extension. `db/init/01-enable-pgvector.sql`
  auto-enables it on a fresh volume; on an existing volume run
  `CREATE EXTENSION IF NOT EXISTS vector;` once.
- **Infra** — Docker / Docker Compose (postgres = `pgvector/pgvector:pg16`),
  Caddy for TLS/reverse proxy at the edge (`Caddyfile`, `DOMAIN` in `.env`),
  nginx inside the frontend image serving the Vite build, AWS EC2.
  `docker-compose.local.yml` builds from source; `docker-compose.yml` pulls
  published images. Backend runtime image is **glibc** (`eclipse-temurin:21-jre`,
  not alpine) — ONNX Runtime for MiniLM needs libstdc++.

## Layout

- `backend/` — base package `com.theskysid.echobackend`, by feature:
  `auth`, `friendship`, `messaging` (DMs + presence), `channel`, `call`
  (LiveKit + Deepgram), `memory` (embeddings, pgvector, decisions, RAG),
  `user`, `config` (incl. `LlmConfig` — the Groq `ChatLanguageModel` bean).
- `frontend/src/` — `pages/`, `components/` (`chat/` incl. `AskAiWidget`,
  `MemoryPanel`; `ui/` for shared primitives), `services/` (Axios clients),
  `hooks/`, `lib/`, `utils/`, `styles/`.

## How it connects

- REST under `/api/**` (JWT) and `/auth/**`. RAG: `GET /api/channels/{id}/ask?q=`
  → `{ answer, sourceIds }` (member-gated).
- WebSocket at `/ws` (SockJS/STOMP). Channels: send `/app/channel/{id}/send`,
  subscribe `/topic/channel/{id}`. DMs: `/app/dm.sendMessage` → `/user/{u}/queue/dm`.
  Presence only on `/topic/public`. Config in `config/WebSocketConfig`.
- Async ingestion (`@EnableAsync`): `ChannelService.postMessage` and
  `TranscriptionController` (after the transcript row is saved)
  fire `MemoryIngestionService` `@Async` methods → embed + LLM decision/supersession
  → pgvector, never blocking the broadcast.

## Layering conventions

Per feature: `entity/` → `repository/` (`JpaRepository`, `@Query` + `JOIN FETCH`,
native pgvector queries) → `service/` (`@Service`, `@Transactional`) →
`controller/` (`@RestController` under `/api/...`, thin). WebSocket handlers in
`*/websocket/` (exception: the presence handler `messaging/controller/ChatController`).
DTOs in `dto/`; external config via `application.yml` → `@Value`.
LLM injected as the `ChatLanguageModel` interface (provider-agnostic).
Security: `JwtAuthenticationFilter` + `SecurityConfig`; `/api/**` requires auth.
