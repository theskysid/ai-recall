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
  (cosine), and the LLM (Groq / Llama 3) synthesizes a grounded answer,
  returning `{ answer, sourceIds }`. Frontend **Ask AI** widget pinned above
  each channel feed.
- **Decisions + supersession** — the LLM flags messages that state a final
  decision (`is_decision`); a newer decision that replaces an older one sets the
  old row's `supersedes_id`. Retrieval behaviour is set by
  `recall.retrieval.mode` (`RECALL_RETRIEVAL_MODE`): `filter` (default) excludes
  superseded vectors outright, `demote` applies a +10 distance penalty,
  `baseline` ignores supersession.
- **Memory panel** — collapsible per-channel panel with decision timeline
  (active + superseded) and call transcripts
  (`GET /api/channels/{id}/decisions`, `/transcripts`).
- **Retrieval evaluation** — `recall.eval.enabled=true` exposes `/api/eval/**`
  (seed messages/transcripts through the real ingestion path, ask with retrieval
  internals returned). Harness and dataset in `ai/eval/` (`run_eval.py`,
  `corpus.json`, `staleness-dataset.md`). Off in prod.

## Removed

- **Global/public chat** — superseded by channels (presence kept).
- **Phone/SMS OTP + Twilio** — email OTP + Google + password remain.
