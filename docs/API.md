# API

## REST Endpoints

### Authentication (`/auth`)
- `POST /auth/signup`
- `POST /auth/login`
- `POST /auth/signup/verify`
- `POST /auth/logout`
- `GET /auth/getonlineusers`
- `GET /auth/getcurrentuser`
- `POST /auth/google/login`
- `POST /auth/email-otp/send`
- `POST /auth/email-otp/verify`

### Channels (`/api/channels`)
- `GET /api/channels` - List channels
- `POST /api/channels` - Create channel
- `POST /api/channels/join` - Join channel
- `DELETE /api/channels/{id}/leave` - Leave channel
- `GET /api/channels/{id}/messages` - Fetch channel messages

### Conversations / DMs (`/api/conversations`)
- `GET /api/conversations` - List user's conversations
- `GET /api/conversations/{id}/messages` - Fetch messages in conversation
- `PUT /api/conversations/{id}/retention` - Update retention policy
- `POST /api/conversations/with/{username}` - Get or create conversation with user

### Calls & Transcriptions (`/api/channels/{id}`)
- `GET /api/channels/{channelId}/call-token` - Generate LiveKit token
- `GET /api/channels/{channelId}/call-status` - Check active call status
- `POST /api/channels/{channelId}/transcribe` - Start audio transcription via Deepgram
- `POST /api/channels/{channelId}/recording` - Upload recorded audio for transcription
- `GET /api/channels/{channelId}/transcripts` - List transcripts for channel

### AI & Memories (`/api/channels/{channelId}`)
- `GET /api/channels/{channelId}/ask` - Ask a question using RAG against memory_vectors
- `GET /api/channels/{channelId}/decisions` - Fetch synthesized decisions from memories

### Eval / Dev (`/api/eval`)
- `POST /api/eval/channels/{channelId}/message` - Seed a test message
- `POST /api/eval/channels/{channelId}/transcript` - Seed a test transcript
- `GET /api/eval/channels/{channelId}/memory-count` - Check memory stats
- `GET /api/eval/channels/{channelId}/ask` - Test AI RAG
- `GET /api/eval/llm-errors` - Extractor and classifier failure counts since startup

## STOMP / WebSocket Destinations

- Endpoint: `/ws`

### Server to Client (Topics/Queues)
- `/topic/public` - Broadcasts global online presence (`JOIN`)
- `/topic/channel/{channelId}` - Broadcasts messages/events for a specific channel
- `/user/{username}/queue/dm` - Personal queue for direct messages

### Client to Server (App mappings)
- `SEND /app/chat.addUser` - Register session and broadcast online status
- `SEND /app/channel/{channelId}/send` - Send a channel message (CHAT, JOIN, LEAVE, TYPING)
- `SEND /app/dm.sendMessage` - Send a direct message
- `SEND /app/dm.typing` - Send a typing indicator for DMs
