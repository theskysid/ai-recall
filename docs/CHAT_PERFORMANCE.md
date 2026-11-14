# Chat delivery performance

The frontend opens a persistent SockJS/STOMP connection directly to the backend.
Separate static frontend hosting does not add a frontend-server hop to each message.
Browser-to-backend and backend-to-database latency still matter.

## Changes

- DM typing notifications send immediately, then at most once per second while typing
  in the same conversation. The server also drops excess typing frames per session
  before doing database work. Actual messages are never throttled by this gate.
- A single DM query checks the server-authenticated user id, current username,
  conversation participation, and accepted friendship, and fetches both participants.
  Payload usernames cannot establish identity. Messages are broadcast after the
  persistence transaction commits.
- Channel subscription/delivery authorization combines current identity and membership
  in one query. Existing subscriptions stop receiving after a member leaves.
- Memory extraction, titles, and conflict classification run outside the write
  transaction. A short transaction rechecks/locks the channel and affected decision
  rows before writing. Stale model assessments cannot overwrite a newer decision state,
  and queued ingestion cannot recreate memories after channel deletion.

## Browser timing

Opt in from the browser's developer console:

```js
localStorage.setItem('recall.chatDiagnostics', '1');
```

Send a DM or channel message. The sender's console reports `[chat timing]` with:

- `echoRoundTripMs`: this browser sending the frame until its confirmed server echo.
- `serverMs`: backend ingress through authorization, persistence, routing, and outbound
  authorization for that echo; carried in the `x-chat-server-ms` STOMP header.
- `residualRoundTripMs`: echo round trip minus server time. This includes both network
  directions, socket writes, protocol decoding, and browser scheduling; it is not a
  one-way network measurement or proof of a hosting-region problem.

These durations use monotonic clocks on each machine and do not compare users' wall
clocks. Diagnostics log no message content, usernames, or tokens. Pending samples are
bounded to 100 and expire after 60 seconds. Disable with:

```js
localStorage.removeItem('recall.chatDiagnostics');
```

## Backend timing

Authenticated Actuator metrics are available on the backend's `/actuator/metrics`
endpoint. The production Caddy configuration does not proxy this path publicly;
read it on the backend host at `http://127.0.0.1:8080` with a valid JWT.

| Metric | Meaning |
|---|---|
| `chat.server.wait` | Inbound authorization and executor queue time before the app handler |
| `chat.server.handle` | Application handler time, including the DM transaction commit |
| `chat.server.total` | Ingress through completion of the app handler |
| `chat.server.delivery` | Ingress through the outbound authorization check for each delivery |
| `chat.database` | DM query/persistence stages, tagged by `operation` |

Server metrics use fixed event tags: `dm.send`, `dm.typing`, `channel.send`.
Database operations use `dm.authorize`, `dm.persist`, and `dm.friendship`.
`dm.persist` measures persistence work inside the service; transaction commit is
included in the overall handler timing. Delivery metrics count recipients, so their
sample count can exceed the send count.

For example, read `/actuator/metrics/chat.server.delivery?tag=event:dm.send` and
`/actuator/metrics/chat.database?tag=operation:dm.authorize`. High database-stage
times point to database work or connection waits; high residual round trips with
low server times point to the network/socket/browser portion. Collect samples from
the affected deployment before concluding which is responsible for user-visible lag.

## Repeatable local benchmark

Use a disposable **local** PostgreSQL database named `recall_chat_benchmark`.
The test recreates tables in that database; it does not need pgvector or external APIs.

```bash
cd backend
RECALL_CHAT_TEST_DB_URL='jdbc:postgresql://127.0.0.1:5432/recall_chat_benchmark?user=postgres' \
  ./mvnw -Dtest=ChatDeliveryBenchmarkIT test
```

The test connects two real SockJS/STOMP clients, authenticates them with JWTs, creates
an accepted friendship, sends five warmup messages and thirty measured DMs, and
requires both recipient delivery and sender confirmation. Hibernate statistics count
SQL statements. The test also checks timing headers/metrics and rejects outsiders,
renamed identities, and revoked friendships. The SQL regression limit is five
statements per measured message; timing itself has no machine-dependent pass limit.

Measured on the same local PostgreSQL 16/server/client setup on November 14, 2026:

| Measure | Before | After |
|---|---:|---:|
| SQL statements per DM, including both deliveries | 7 | 5 |
| Median delivery plus sender confirmation | 13.568 ms | 9.878 ms |
| p95 delivery plus sender confirmation | 19.449 ms | 14.334 ms |

The baseline used commit `9d4a91f` with the same benchmark protocol. These are small
localhost samples, not production measurements or a guarantee of an equivalent
speedup on the deployed site. The public backend endpoint was unreachable from the
diagnostic environment, so live delivery timing remains to be collected after deploy.
