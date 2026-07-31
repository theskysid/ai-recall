# Project

**Recall** (codebase: Echo Messaging) — a real-time chat + AI-memory app.
Spring Boot + React over WebSocket (STOMP/SockJS), PostgreSQL 16 with pgvector.
Channels can hold text chat, video calls, transcription, and a searchable
vector "memory" of everything said.

## Features today

- **Authentication** — password login/signup, email OTP, Google OAuth2.
  Stateless JWT in an httpOnly cookie. (Phone/SMS OTP has been removed.)
- **Friendship + Direct Messages** — search/add friends, real-time 1:1
  ephemeral DMs with per-conversation retention, online presence.
- **Channels** — create (auto invite code) / join by code / leave (owner
  transfers to longest-standing member) / list. Real-time per-channel
  messaging over STOMP with persisted history.
- **Video calls** — LiveKit WebRTC per channel; backend mints scoped access
  tokens (`/call-token`), frontend renders the room.
- **Transcription** — Deepgram batch transcription of call audio, stored as
  `CallTranscript` (`/transcribe`).
- **Vector memory (RAG)** — messages and transcripts are embedded locally
  (all-MiniLM-L6-v2, 384-dim) via an async pipeline and stored in pgvector.
  `/ask?q=` retrieves the top-5 channel-scoped memories (cosine similarity).

## Removed

- **Global/public chat** — deleted; superseded by channels. Presence
  (`/topic/public` JOIN/LEAVE) is kept for online status.
- **Phone/SMS OTP + Twilio** — removed; email OTP + Google + password remain.

## Planned next

- LLM answer generation on top of RAG retrieval (`/ask` currently returns
  context + source ids only).
- Frontend UI for asking questions and viewing transcripts.
