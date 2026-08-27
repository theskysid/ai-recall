# API

## REST Endpoints

Everything is authenticated except the routes marked **public**
(`SecurityConfig` permits `/auth/login`, `/auth/signup`, `/auth/signup/verify`,
`/auth/email-otp/**`, `/auth/google/**`, `/actuator/health`, `/ws/**`;
`anyRequest().authenticated()` covers the rest). The JWT travels in an httpOnly
`JWT` cookie set by login / signup verify / OTP verify / Google login — not an
`Authorization` header.

### Authentication (`/auth`)
- `POST /auth/signup` — **public**, body `{username, email, password}`
- `POST /auth/login` — **public**, body `{username, password}`, sets the `JWT` cookie
- `POST /auth/signup/verify` — **public**, body `{username, identifier, password, otp}`, sets the `JWT` cookie
- `POST /auth/logout`
- `GET /auth/getonlineusers`
- `GET /auth/getcurrentuser`
- `POST /auth/google/login` — **public**, body `{idToken}`, sets the `JWT` cookie
- `POST /auth/email-otp/send` — **public**, body `{email}`
- `POST /auth/email-otp/verify` — **public**, body `{email, otp}`, sets the `JWT` cookie

### Channels (`/api/channels`)
- `GET /api/channels` - List channels
- `POST /api/channels` - Create channel — body `{name, description}`
- `POST /api/channels/join` - Join channel — body `{inviteCode}`
- `DELETE /api/channels/{id}/leave` - Leave channel
- `GET /api/channels/{id}/messages` - Fetch channel messages

### Friends (`/api/friends`)
- `GET /api/friends` - List accepted friends
- `GET /api/friends/requests/incoming` - List pending incoming requests
- `GET /api/friends/requests/rejected` - List rejected requests
- `GET /api/friends/search?q={query}` - Search users by username (`q` required)
- `POST /api/friends/request` - Send a friend request — body `{addresseeUsername}`
- `POST /api/friends/accept/{id}` - Accept an incoming request
- `POST /api/friends/reject/{id}` - Reject an incoming request
- `DELETE /api/friends/cancel/{id}` - Cancel an outgoing request
- `DELETE /api/friends/{id}` - Remove a friend

### Profile (`/api/profile`)
- `GET /api/profile` - Current user's profile
- `PUT /api/profile` - Update profile — body `{displayName, bio, username}`
- `POST /api/profile/link-email/send` - Send OTP to an email for linking — body `{email}`
- `POST /api/profile/link-email/verify` - Verify OTP and link the email — body `{email, otp}`
- `POST /api/profile/link-google` - Link a Google account — body `{idToken}`
- `POST /api/profile/unlink-email` - Remove the email from the account — no body
- `POST /api/profile/unlink-google` - Remove Google from the account — no body

### Conversations / DMs (`/api/conversations`)
- `GET /api/conversations` - List user's conversations
- `GET /api/conversations/{id}/messages?page=0&size=50` - Fetch messages in conversation — returns a paged envelope (`{content, totalElements, totalPages, number, size, ...}`), not a bare list
- `PUT /api/conversations/{id}/retention` - Update retention policy
- `POST /api/conversations/with/{username}` - Get or create conversation with user

### Calls & Transcriptions (`/api/channels/{id}`)
- `GET /api/channels/{channelId}/call-token` - Generate LiveKit token
- `GET /api/channels/{channelId}/call-status` - Check active call status
- `POST /api/channels/{channelId}/transcribe` - Start audio transcription via Deepgram — body `{audioUrl}`
- `POST /api/channels/{channelId}/recording` - Upload recorded audio for transcription — `multipart/form-data` with a `file` part; returns 204 No Content when the transcript comes back blank, otherwise the `CallTranscriptDTO`
- `GET /api/channels/{channelId}/transcripts` - List transcripts for channel

### AI & Memories (`/api/channels/{channelId}`)
- `GET /api/channels/{channelId}/ask?q={query}` - Ask a question using RAG against memory_vectors (`q` required) — returns `{answer: string, sourceIds: number[]}`
- `GET /api/channels/{channelId}/decisions` - Fetch synthesized decisions from memories

## STOMP / WebSocket Destinations

- Endpoint: `/ws`

### Server to Client (Topics/Queues)
- `/topic/public` - Broadcasts global online presence (`JOIN`)
- `/topic/channel/{channelId}` - Broadcasts messages/events for a specific channel
- `/user/{username}/queue/dm` - Personal queue for direct messages
- `/user/{username}/queue/friends` - Personal queue for friend request and acceptance events

### Client to Server (App mappings)
- `SEND /app/chat.addUser` - Register session and broadcast online status
- `SEND /app/channel/{channelId}/send` - Send a channel message (CHAT, JOIN, LEAVE, TYPING). Only `CHAT` is persisted; `TYPING`/`JOIN`/`LEAVE` are broadcast only, and an unknown or blank `type` falls back to `CHAT`
- `SEND /app/dm.sendMessage` - Send a direct message
- `SEND /app/dm.typing` - Send a typing indicator for DMs
