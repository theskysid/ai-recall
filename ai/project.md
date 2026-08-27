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
  (`/call-token`), reports in-progress calls (`/call-status`), and accepts the
  browser's recorded call mix (`/recording`, 100MB multipart cap); frontend
  renders the room. Verified working end-to-end.
- **Transcription** — Deepgram batch transcription of call audio (`/transcribe`),
  stored as `CallTranscript`.
- **Vector memory + RAG** — messages and transcripts are embedded locally
  (all-MiniLM-L6-v2, 384-dim) via an async pipeline into pgvector.
  `GET /ask?q=` embeds the query, retrieves the top-5 channel-scoped memories
  (cosine), and the LLM (Groq, OpenAI-compatible; gpt-oss models, ids from
  `GROQ_MODEL` / `GROQ_FAST_MODEL`) synthesizes a grounded answer,
  returning `{ answer, sourceIds }`. Frontend **Ask AI** widget pinned above
  each channel feed.
- **Decisions, supersession + conflicts** — the LLM flags messages that state a
  final decision (`is_decision`); a second call classifies a new decision
  against the earlier one it clashes with. Either the new one supersedes the old
  (old row gets `supersedes_id` and status `SUPERSEDED`, excluded from retrieval
  outright, so a decision the team reversed can never reach the prompt), or
  neither side wins and both rows are marked `UNRESOLVED` and cross-linked by
  `conflicts_with_id` — still retrieved normally, only flagged.
- **Memory panel** — collapsible per-channel panel with decision timeline
  (active, superseded, and an unresolved-clash tag) and call transcripts
  (`GET /api/channels/{id}/decisions`, `/transcripts`).

## Removed

- **Global/public chat** — superseded by channels (presence kept).
- **Phone/SMS OTP + Twilio** — email OTP + Google + password remain.
