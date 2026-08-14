# Status

## Feature Matrix

| Feature | Status | Notes |
|---------|--------|-------|
| Local Auth | Working | Signup, login, logout, OTP flow implemented. |
| Google OAuth | Working | Endpoint `/auth/google/login` is implemented. |
| Channels | Working | Create, join, leave, message fetching are wired up. |
| Direct Messages | Working | Handled separately from Channels, via `Conversations` and STOMP `/user/{username}/queue/dm`. |
| Presence | Working | Handled via `/app/chat.addUser` broadcasting to `/topic/public`. |
| LiveKit Calling | Working | Token generation (`/call-token`) is functional. |
| Call Transcription | **In Progress** | Wireup exists but **text extraction quality issue** is known. |
| Vector DB Storage | Working | Uses Postgres `pgvector` inside the `memory_vectors` table (Not a separate DB instance!). |
| AI RAG (`/ask`) | **In Progress** | Endpoint exists but **Recall chat context retrieval is partial**. |

## Architecture Confirmations

- **Vector DB Behavior**: Confirmed that the codebase aligns with the locked decision to use a single Postgres+pgvector store (no separate standalone vector DB). All vectors are stored directly in PostgreSQL within the `memory_vectors` table.
- **ML Pipeline Location**:
  - **Originally planned**: Separate Python FastAPI sidecar for embeddings.
  - **Actual**: Implemented natively in Spring Boot (`EmbeddingService`, `AiController`, `EvalController`) via LangChain4j.
  - **Status**: This is now a locked decision per `DECISIONS.md`, not an open discrepancy — no action needed.

## Discrepancies and Clarifications

- **Call Transcription Quality**: Code handles uploading recordings and interfacing with Deepgram, but transcription quality issues persist in extraction.
- **Dual Store Fragmentation**: DM functionality and Channel Messaging currently use separate tables (`direct_messages` vs `channel_messages`). This requires future consolidation.
