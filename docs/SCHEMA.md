# Schema

The current database uses a single Postgres database with `pgvector`. Tables are automatically generated via Hibernate's `ddl-auto: update`.

## Tables

### `users`
- `id` (BIGINT, PK)
- `username` (VARCHAR, Unique)
- `email` (VARCHAR, Unique)
- `password` (VARCHAR)
- `auth_provider` (ENUM: LOCAL, GOOGLE)
- `google_id` (VARCHAR)
- `avatar_url` (VARCHAR)
- `is_online` (BOOLEAN)
- `bio` (VARCHAR)
- `created_at` (TIMESTAMP)

### `channel`
- `id` (BIGINT, PK)
- `name` (VARCHAR)
- `owner_id` (BIGINT, FK to `users`)
- `created_at` (TIMESTAMP)

### `channel_memberships`
- `id` (BIGINT, PK)
- `channel_id` (BIGINT, FK to `channel`)
- `user_id` (BIGINT, FK to `users`)
- `joined_at` (TIMESTAMP)

### `channel_messages`
- `id` (BIGINT, PK)
- `channel_id` (BIGINT, FK to `channel`)
- `sender_id` (BIGINT, FK to `users`)
- `content` (VARCHAR 2000)
- `color` (VARCHAR)
- `timestamp` (TIMESTAMP)
- `type` (ENUM: CHAT, JOIN, LEAVE, TYPING)

### `conversations` (Direct Messages)
- `id` (BIGINT, PK)
- `user1_id` (BIGINT, FK to `users`)
- `user2_id` (BIGINT, FK to `users`)
- `retention_policy` (ENUM)

### `direct_messages`
- `id` (BIGINT, PK)
- `conversation_id` (BIGINT, FK to `conversations`)
- `sender_id` (BIGINT, FK to `users`)
- `content` (TEXT)
- `timestamp` (TIMESTAMP)
- `expires_at` (TIMESTAMP)

> **TODO / FLAG**: Dual-store fragmentation. The schema currently splits messaging between `channel_messages` and `direct_messages`. These are separate stores that need consolidating into one `messages` table with a `channel.type` attribute.

### `memory_vectors` (pgvector)
- `id` (UUID, PK)
- `channel_id` (BIGINT)
- `content` (TEXT)
- `embedding` (vector(384))
- `source_type` (ENUM)
- `source_id` (BIGINT)
- `is_decision` (BOOLEAN)
- `title` (VARCHAR)
- `status` (VARCHAR 20)
- `supersedes_id` (UUID)
- `conflicts_with_id` (UUID)
- `created_at` (TIMESTAMP)

### `call_transcripts`
- `id` (BIGINT, PK)
- `channel_id` (BIGINT, FK to `channel`)
- `content` (TEXT)
- `created_at` (TIMESTAMP)

### `friendships`
- `id` (BIGINT, PK)
- `requester_id` (BIGINT, FK to `users`)
- `addressee_id` (BIGINT, FK to `users`)
- `status` (ENUM)

### `otp_verifications`
- `id` (BIGINT, PK)
- `email` (VARCHAR)
- `otp` (VARCHAR)
- `expires_at` (TIMESTAMP)
- `verified` (BOOLEAN)
