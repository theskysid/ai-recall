# Architecture

## Tech stack

- **Backend** — Spring Boot 3.3.4, Java 21, Spring Web, Spring Security,
  Spring Data JPA/Hibernate, WebSocket (STOMP), JWT (jjwt 0.12), Spring Mail,
  Twilio SDK, Google API client.
- **Frontend** — React 19, Vite 7, Axios, `@stomp/stompjs` + `sockjs-client`,
  `react-router-dom` 7, `@react-oauth/google`.
- **Database** — PostgreSQL 16. Schema managed by Hibernate `ddl-auto: update`
  (no migration tool).
- **Infra** — Docker / Docker Compose, nginx (serves built frontend), AWS EC2.

## Layout

- `echo-backend/` — Maven project, base package
  `com.theskysid.echobackend`, organized by **feature package**:
  `auth`, `friendship`, `messaging`, `channel`, `user`, `config`.
- `echo-frontend/` — Vite app under `src/`: `pages/`, `components/`
  (`chat/`, `ui/`), `services/` (Axios API clients), `hooks/`, `styles/`.
- Root — `docker-compose.yml` (prod images), `docker-compose.local.yml`
  (builds from source), `.env` (single shared env file).

## How it connects

- Frontend calls REST under `/api/**` and `/auth/**` via Axios (`services/`).
- WebSocket: client connects to `/ws` (SockJS) using STOMP. App-bound messages
  use the `/app` prefix; broker topics are `/topic`, `/queue`, `/user`.
  Public chat broadcasts to `/topic/public`; private/user events go to
  `/user/{username}/queue/...`. Config in `config/WebSocketConfig`.

## Layering conventions

Per feature: `entity/` (JPA `@Entity`) → `repository/` (`JpaRepository` with
`@Query` + `JOIN FETCH`) → `service/` (`@Service`, `@Transactional`, business
logic) → `controller/` (`@RestController` under `/api/...`, thin). WebSocket
handlers live in `messaging/websocket/`. DTOs live in `dto/` and never expose
entities directly; requests are `...RequestDTO`, responses are `...DTO`.
Security: `JwtAuthenticationFilter` + `SecurityConfig`; `/api/**` requires auth.
