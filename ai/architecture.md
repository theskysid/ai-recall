# Architecture

## Tech stack

- **Backend** — Spring Boot 3.3.4, Java 21, Spring Web, Security, Data
  JPA/Hibernate, WebSocket (STOMP), JWT (jjwt 0.12), Spring Mail, Google API
  client. (Twilio removed with phone OTP.)
- **AI / calls** — LiveKit Server SDK (`io.livekit:livekit-server`) for WebRTC
  tokens; Deepgram batch API via `java.net.http.HttpClient`; langchain4j
  all-MiniLM-L6-v2 (local, in-process embeddings, 384-dim); pgvector
  (`com.pgvector:pgvector`) for similarity search.
- **Frontend** — React 19, Vite 7, Axios, `@stomp/stompjs` + `sockjs-client`,
  `react-router-dom` 7, `@react-oauth/google`, `@livekit/components-react`.
- **Database** — PostgreSQL 16 + `vector` extension (run
  `CREATE EXTENSION IF NOT EXISTS vector;` once — not managed by `ddl-auto`).
- **Infra** — Docker / Docker Compose, nginx (serves built frontend), AWS EC2.

## Layout

- `echo-backend/` — base package `com.theskysid.echobackend`, by **feature
  package**: `auth`, `friendship`, `messaging` (DMs + presence), `channel`,
  `call` (LiveKit tokens + Deepgram transcripts), `memory` (embeddings,
  pgvector, RAG), `user`, `config`.
- `echo-frontend/` — Vite app under `src/`: `pages/`, `components/`
  (`chat/`, `ui/`), `services/` (Axios API clients), `hooks/`, `styles/`.
- Root — `docker-compose.yml` (prod), `docker-compose.local.yml` (source
  build), `.env` (single shared env file).

## How it connects

- Frontend calls REST under `/api/**` (JWT-authenticated) and `/auth/**`.
- WebSocket at `/ws` (SockJS/STOMP). App prefix `/app`; broker `/topic`,
  `/queue`, `/user`. Channels: send `/app/channel/{id}/send`, subscribe
  `/topic/channel/{id}`. DMs: `/app/dm.sendMessage` → `/user/{u}/queue/dm`.
  Presence only on `/topic/public` (JOIN/LEAVE). Config in `config/WebSocketConfig`.
- Async ingestion (`@EnableAsync`): `ChannelService.postMessage` and transcript
  save fire `MemoryIngestionService` `@Async` methods so embedding never blocks
  the WebSocket broadcast.

## Layering conventions

Per feature: `entity/` (JPA `@Entity`) → `repository/` (`JpaRepository`,
`@Query` + `JOIN FETCH`, native pgvector queries) → `service/` (`@Service`,
`@Transactional`) → `controller/` (`@RestController` under `/api/...`, thin).
WebSocket handlers live in `*/websocket/`. DTOs in `dto/` (`...RequestDTO` /
`...DTO`); external config read via `application.yml` → `@Value`.
Security: `JwtAuthenticationFilter` + `SecurityConfig`; `/api/**` requires auth.
