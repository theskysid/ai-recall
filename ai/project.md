# Project

**Recall** (codebase: Echo Messaging) — a real-time chat + AI-memory app.
Spring Boot + React over WebSocket (STOMP/SockJS), PostgreSQL 16 with pgvector.
Channels hold text chat, video calls, transcription, and a searchable vector
"memory" that answers questions about everything said in the channel.

## Features today

- **Authentication** — password, email OTP, Google OAuth2. JWT in an httpOnly
  cookie. (Phone/SMS OTP removed.)
- **Friendship + Direct Messages** — add friends, real-time 1:1 ephemeral DMs
  with per-conversation retention, online presence.
- **Channels** — create (auto invite code) / join by code / leave (owner
  transfers to longest-standing member) / list. Real-time per-channel messaging
  over STOMP with persisted history.
- **Video calls** — LiveKit WebRTC per channel; backend mints scoped tokens
  (`/call-token`), frontend renders the room. Verified working end-to-end.
- **Transcription** — Deepgram batch transcription of call audio (`/transcribe`),
  stored as `CallTranscript`.
- **Vector memory + RAG** — messages and transcripts are embedded locally
  (all-MiniLM-L6-v2, 384-dim) via an async pipeline into pgvector.
  `GET /ask?q=` embeds the query, retrieves the top-5 channel-scoped memories
  (cosine), and the LLM (Groq / Llama 3) synthesizes a grounded answer,
  returning `{ answer, sourceIds }`. Frontend **Ask AI** widget pinned above
  each channel feed.
- **Decisions + supersession** — the LLM flags messages that state a final
  decision (`is_decision`); a newer decision that replaces an older one sets the
  old row's `supersedes_id`, and retrieval demotes superseded vectors.

## Removed

- **Global/public chat** — superseded by channels (presence kept).
- **Phone/SMS OTP + Twilio** — email OTP + Google + password remain.

## Planned next

- Frontend UI to view call transcripts and a decision timeline/history.
- Fix Deepgram env key (`.env` has `DEEPGRAM_SECRET`; code reads `DEEPGRAM_API_KEY`)
  and verify the transcription → memory path end-to-end.
