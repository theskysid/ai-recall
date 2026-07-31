# Conventions

## Naming

- Packages by feature (`auth`, `friendship`, `messaging`, `channel`, `call`,
  `memory`, `user`).
- Classes PascalCase; suffix by role: `...Controller`, `...Service`,
  `...Repository`, `...DTO`, request bodies `...RequestDTO`.
- REST base path `/api/<feature>` (plural, e.g. `/api/friends`,
  `/api/channels`); auth endpoints under `/auth/...`. Call/AI endpoints are
  channel-scoped (`/api/channels/{id}/call-token`, `/transcribe`, `/ask`).
- DB tables/columns snake_case via `@Table` / `@Column(name = ...)`.

## Entities & DTOs

- Entities: Lombok `@Data @Builder @NoArgsConstructor @AllArgsConstructor`,
  `@ManyToOne(fetch = LAZY)` + `@JoinColumn`, timestamps set by
  `@PrePersist` / `@PreUpdate`. Unique constraints declared on `@Table`.
- DTOs: Lombok `@Data @Builder` (response) or plain `@Data` (request).
  Controllers map entity → DTO with private `toXxxDTO(...)` helpers.

## Dependency injection

- Field injection with `@Autowired` (the existing repo style), not
  constructor injection.

## External APIs, config & async

- External-service creds read from env via `application.yml` → `@Value`
  (e.g. `livekit.api-key`, `deepgram.api-key`); blank defaults (`:}`) so the
  app starts unconfigured and fails cleanly at request time.
- External HTTP calls use `java.net.http.HttpClient` (Deepgram); LiveKit uses
  its SDK. New env vars must be added to `.env.example`.
- Background work uses `@Async` (`@EnableAsync` on the app class); embedding
  ingestion runs off the request/broadcast thread and only logs on failure.
- pgvector: `float[]` fields mapped via the custom `memory/entity/VectorType`
  `UserType`; similarity search uses native `@Query` with `<->` / `<=>` and
  `CAST(:vec AS vector)`, always filtered by `channel_id` first.

## Error handling

- Services throw `new RuntimeException("message")` for business errors.
- Controllers wrap calls in try/catch and return
  `ResponseEntity.badRequest().body(Map.of("error", e.getMessage()))`.
- Unauthenticated: `if (authentication == null)` →
  `ResponseEntity.status(UNAUTHORIZED).body(Map.of("error", "Not authenticated"))`.
- Current user resolved via
  `authenticationService.resolveAuthenticatedUser(authentication.getName())`.
- Identifiers normalized through `auth/util/IdentifierNormalizer`.

## Testing

- Minimal: only the default Spring Boot context-load smoke test
  (`EchoBackendApplicationTests`). No unit/integration suite, no frontend
  tests. Frontend uses ESLint (`npm run lint`). Verify changes by building
  (`./mvnw package`, `npm run build`) and running the app.
