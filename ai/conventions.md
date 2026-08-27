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
  (e.g. `livekit.api-key`, `deepgram.api-key`, `spring.ai.groq.api-key`); blank
  defaults (`:}`) so the app starts unconfigured and fails cleanly at request time.
- External HTTP calls use `java.net.http.HttpClient` (Deepgram) or an SDK
  (LiveKit). New env vars must be added to `.env.example`.
- **LLM** is injected as the `ChatLanguageModel` interface; the concrete beans
  live in `config/LlmConfig` (`langchain4j-open-ai` → Groq base URL) — the
  `@Primary` reasoning model, and the fast one reached via
  `@Qualifier("fastChatLanguageModel")`. Model ids come from
  `spring.ai.groq.model` / `spring.ai.groq.fast-model`. Services stay
  provider-agnostic.
- LLM failures are never coerced into a negative answer. The RAG answer falls
  back to the raw retrieved context (`RagService`); the decision extractor and
  the conflict classifier return a distinct `LLM_ERROR` result (never
  `NOT_A_DECISION` / `NO_CONFLICT`), which is logged with a marker, counted, and
  treated as "do nothing" — a throttled model must not read as "nobody decided
  anything".
- Background work uses `@Async` (`@EnableAsync` on the app class); embedding +
  decision ingestion runs off the request/broadcast thread and only logs on
  failure. LLM calls inside it are synchronous so the DB ends up consistent.
- pgvector: `float[]` fields mapped via the custom `memory/entity/VectorType`
  `UserType`; similarity search uses native `@Query` with `<=>` (cosine
  distance) and `CAST(:vec AS vector)`, always filtered by `channel_id` first.

## Schema gotcha (ddl-auto on populated tables)

`ddl-auto: update` cannot add a `NOT NULL` column to a table that already has
rows unless it has a DB default. For new non-null columns give a
`columnDefinition` with a default (e.g.
`@Column(columnDefinition = "boolean not null default false")`), or `ALTER TABLE`
manually. pgvector's `vector` extension is never created by `ddl-auto`.

## Error handling

- Services throw `new RuntimeException("message")` for a bad request, and
  `ResponseStatusException` when the status matters (403 for a non-member).
- Controllers return the DTO directly and never try/catch.
- `config/ApiExceptionHandler` (`@RestControllerAdvice`) turns both into
  `{"error": "..."}`.
- 401 is Spring's, from `SecurityConfig`'s `anyRequest().authenticated()` —
  nothing in the app code handles it.
- Current user resolved via
  `authenticationService.resolveAuthenticatedUser(authentication.getName())`.
- Identifiers normalized through `auth/util/IdentifierNormalizer`.

## Testing

- Minimal: the Spring Boot context-load smoke test
  (`EchoBackendApplicationTests`) plus focused unit tests where logic is worth
  pinning (`auth/util/IdentifierNormalizerTest`, `config/LlmConfigTest`, and
  `memory/service/DecisionServiceTest`, which pins the extractor/classifier
  parsing). No integration suite, no frontend tests. Frontend uses ESLint
  (`npm run lint`). Verify changes by building (`./mvnw package`,
  `npm run build`) and running the app.
