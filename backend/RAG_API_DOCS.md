# RAG / AI Memory API — Postman Reference

Base URL (local): `http://localhost:8080`

All `/api/**` endpoints require a JWT. Get one from `/auth/login`, then send it as:

```
Authorization: Bearer <token>
```

(The login response also sets an httpOnly `JWT` cookie; either the header or the
cookie works. For Postman, use the Bearer header.)

---

## 0. Login — get a token

`POST /auth/login`

Headers: `Content-Type: application/json`

Request body:
```json
{ "username": "rxsiddhant", "password": "siddhant" }
```

`200 OK`:
```json
{
  "id": 5,
  "username": "rxsiddhant",
  "email": "user@example.com",
  "displayName": null,
  "bio": null,
  "googleId": null,
  "authProvider": "EMAIL",
  "token": "eyJhbGciOiJIUzI1NiJ9....",
  "online": false
}
```

Copy `token` → use as `Authorization: Bearer <token>` for everything below.

> Password login only works for accounts that have a password (EMAIL provider).
> Google-only accounts return `403`.

---

## 1. Ask the channel's AI memory (RAG)  ⭐ main endpoint

`GET /api/channels/{channelId}/ask?q={query}`

| Part | Value |
|------|-------|
| Method | `GET` |
| Path param | `channelId` (long), e.g. `3` |
| Query param | `q` (string, URL-encoded) — the question |
| Header | `Authorization: Bearer <token>` |
| Body | none |

Example: `GET /api/channels/3/ask?q=What is the secret password`

`200 OK`:
```json
{
  "answer": "The database secret password is banana-pancake.",
  "sourceIds": [3]
}
```

- `answer` — natural-language response synthesized by the LLM (Groq / Llama 3),
  grounded only in the channel's stored memory. If the info isn't in memory it
  replies that it doesn't know. If the LLM call fails, it falls back to the raw
  retrieved context text (never crashes).
- `sourceIds` — `source_id`s of the memory chunks used as context (the originating
  message / transcript ids).

Retrieval takes the top 5 vectors scoped to that channel only. By default a
decision that a later decision superseded is **excluded** and can never reach the
prompt. `RECALL_RETRIEVAL_MODE` changes that arm — it exists for the retrieval
evaluation, leave it at `filter` otherwise:

| Mode | Behaviour |
|------|-----------|
| `filter` (default) | superseded decisions excluded outright |
| `demote` | superseded decisions get a +10 distance penalty, so they sort last but can still backfill a sparse channel |
| `baseline` | supersession ignored; plain cosine similarity |

This endpoint returns only `answer` + `sourceIds`. The eval `/ask` (§6) returns
the full `RagContextDTO`, which also carries `mode` and `retrieved`.

### Error responses

| Status | Body | When |
|--------|------|------|
| `403` | *(empty)* | missing/invalid token — Spring Security's default entry point rejects the request before the controller runs |
| `403` | `{ "error": "You are not a member of this channel" }` | not a channel member |
| `400` | `{ "error": "Query is required" }` | `q` blank |
| `400` | Spring's default error body (`timestamp`/`status`/`error`/`path`) | non-numeric `channelId` — fails at `@PathVariable Long` binding |

---

## 2. Supporting endpoints (to set up a RAG test)

### 2a. List your channels
`GET /api/channels` → `200 OK`
```json
[
  {
    "id": 3,
    "name": "rag-test",
    "description": "ingestion test",
    "inviteCode": "RKZ43DCR",
    "ownerUsername": "rxsiddhant",
    "owner": true,
    "memberCount": 1,
    "createdAt": "2026-07-31T08:58:40.46",
    "joinedAt": "2026-07-31T08:58:40.46"
  }
]
```

### 2b. Create a channel
`POST /api/channels`  ·  `Content-Type: application/json`
```json
{ "name": "rag-test", "description": "ingestion test" }
```
`200 OK` → same `ChannelDTO` shape as above (creator becomes owner + member).

### 2c. Join a channel
`POST /api/channels/join`  ·  `Content-Type: application/json`
```json
{ "inviteCode": "RKZ43DCR" }
```

### 2d. Channel message history
`GET /api/channels/{channelId}/messages` → `200 OK`
```json
[
  {
    "id": 3,
    "channelId": 3,
    "sender": "rxsiddhant",
    "content": "The secret password for the database is banana-pancake.",
    "color": "#8b5cf6",
    "type": "CHAT",
    "timestamp": "2026-07-31T08:59:10.12"
  }
]
```

---

## 3. How memory gets populated (important for testing)

There is **no REST endpoint to post a channel message in a normal deployment** —
set `RECALL_EVAL_ENABLED=true` to expose `/api/eval/channels/{id}/message` for
seeding (§6). Otherwise messages are sent over WebSocket (STOMP), which is what
triggers the async embedding pipeline:

- Send:      `/app/channel/{channelId}/send`
- Subscribe: `/topic/channel/{channelId}`
- SockJS endpoint: `ws://localhost:8080/ws`

Payload sent to `/app/channel/{id}/send`:
```json
{ "sender": "rxsiddhant", "content": "your text", "color": "#8b5cf6", "type": "CHAT" }
```

To seed data for a RAG test, the simplest path is the **frontend**: log in, open the
channel, and type messages. Each `CHAT` message is embedded (all-MiniLM-L6-v2, local)
and stored in `memory_vectors` a second or two later. Then call the `/ask` endpoint.

Postman does not send STOMP frames easily, so use the UI (or a STOMP client) to add
messages; use Postman for `/ask` and the REST endpoints above.

---

## 4. Related AI endpoints

### 4a. Call token (LiveKit)
`GET /api/channels/{channelId}/call-token` → `200 OK`
```json
{ "token": "eyJ...", "url": "wss://<project>.livekit.cloud", "room": "3", "identity": "5" }
```

### 4b. Transcribe call audio (Deepgram)
`POST /api/channels/{channelId}/transcribe`  ·  `Content-Type: application/json`
```json
{ "audioUrl": "https://example.com/recording.mp3" }
```
`200 OK`:
```json
{
  "id": 1,
  "channelId": 3,
  "audioUrl": "https://example.com/recording.mp3",
  "fullTranscript": "…",
  "createdAt": "2026-07-31T09:10:00.00"
}
```
Requires `DEEPGRAM_API_KEY` set in `.env`. The saved transcript is also chunked and
embedded into `memory_vectors`, so it becomes searchable via `/ask`.

### 4c. Upload a browser call recording
`POST /api/channels/{channelId}/recording`  ·  `multipart/form-data`, field `file`

Transcribes the uploaded bytes directly (no `audioUrl` round-trip) and files the
result through the same ingestion path. `200 OK` → the same transcript shape as
4b; `204 No Content` when the recording transcribes to nothing (a silent call).
Uploads are capped at 100MB (`spring.servlet.multipart` in `application.yml`).

### 4d. List saved transcripts
`GET /api/channels/{channelId}/transcripts` → `200 OK`, array of the transcript
shape above, most recent first.

### 4e. Call status
`GET /api/channels/{channelId}/call-status` → `200 OK`
```json
{ "active": true, "participants": 2 }
```

### 4f. Channel decisions
`GET /api/channels/{channelId}/decisions` → `200 OK`
```json
[
  {
    "id": "0f0a6f0e-9e2b-4a1e-9f0a-1c2d3e4f5a6b",
    "channelId": 3,
    "content": "We're going with Postgres for the vector store.",
    "sourceType": "MESSAGE",
    "sourceId": 3,
    "superseded": false,
    "createdAt": "2026-07-31T09:00:00.00"
  }
]
```
Extracted decisions for the channel — both active and superseded — newest first.

All of the above are members-only and use the same `403` behaviour as §1.

---

## 5. Quick curl (copy-paste)

```bash
BASE=http://localhost:8080
TOKEN=$(curl -s -X POST $BASE/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"rxsiddhant","password":"siddhant"}' \
  | grep -oE '"token":"[^"]+"' | cut -d'"' -f4)

curl -s -G "$BASE/api/channels/3/ask" \
  --data-urlencode "q=What is the secret password" \
  -H "Authorization: Bearer $TOKEN"
```

---

## 6. Evaluation endpoints (`RECALL_EVAL_ENABLED=true` only)

Absent from a normal deployment — the controller is `@ConditionalOnProperty`.
They exist so the staleness evaluation can seed a corpus over HTTP; everything
still routes through the production ingestion path. Members only, same auth
behaviour as §1.

| Endpoint | Body / params | Purpose |
|----------|---------------|---------|
| `POST /api/eval/channels/{channelId}/message` | `{ "content": "..." }` | Post a chat message without the WebSocket → `{ "id": 12 }` |
| `POST /api/eval/channels/{channelId}/transcript` | `{ "content": "..." }` | File a call transcript without Deepgram → `{ "id": 4 }` |
| `GET /api/eval/channels/{channelId}/memory-count` | — | `{ "count": 7 }` — ingestion is async, poll this until it settles |
| `GET /api/eval/channels/{channelId}/ask?q=` | `q` | Full `RagContextDTO` |

The eval `/ask` returns more than the product one:
```json
{
  "answer": "The database secret password is banana-pancake.",
  "sourceIds": [3],
  "mode": "filter",
  "retrieved": ["The secret password for the database is banana-pancake."]
}
```
`mode` is the active `RECALL_RETRIEVAL_MODE`; `retrieved` is the raw text of every
chunk handed to the LLM, in prompt order — so a retrieval failure can be told
apart from a synthesis failure.
