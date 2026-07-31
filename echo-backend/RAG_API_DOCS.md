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

### Error responses

| Status | Body | When |
|--------|------|------|
| `401` | `{ "error": "Not authenticated" }` | missing/invalid token |
| `403` | `{ "error": "You are not a member of this channel" }` | not a channel member |
| `400` | `{ "error": "Query is required" }` | `q` blank |
| `400` | `{ "error": "For input string: \"abc\"" }` | non-numeric `channelId` |

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

There is **no REST endpoint to post a channel message.** Messages are sent over
WebSocket (STOMP), which is what triggers the async embedding pipeline:

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
