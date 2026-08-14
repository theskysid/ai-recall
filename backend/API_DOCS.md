# Echo Messaging Backend API Documentation

## Base URL
```
http://localhost:8080
```

## Authentication
Include the JWT in one of these ways:
- **Header**: `Authorization: Bearer <token>`
- **Cookie**: `JWT=<token>` (automatically set after login)

Everything requires authentication except the following, which are `permitAll`
in `SecurityConfig`:

```
/auth/login
/auth/signup
/auth/signup/verify
/auth/email-otp/**
/auth/google/**
/actuator/health
/ws/**
```

That includes `/api/**` and `/auth/logout` — all authenticated.

Token lifetime comes from `JWT_EXPIRATION` (currently `3600000` ms = 1 hour).
The `JWT` cookie has its own hardcoded `maxAge` of 3600s.

---

## REST API Endpoints

### Authentication Endpoints

#### POST /auth/signup
**Description**: Register a new user with username + email + password
**Request Body**:
```json
{
  "username": "string",
  "email": "string",
  "password": "string"
}
```
**Response**:
```json
{
  "id": 1,
  "username": "string",
  "email": "string",
  "displayName": null,
  "bio": null,
  "googleId": null,
  "authProvider": "EMAIL",
  "online": false,
  "token": null
}
```
**Note**: `token` is null on this path — only `/auth/login`, `/auth/signup/verify`
and the OTP/Google paths issue a token.

#### POST /auth/signup/verify
**Description**: Complete an OTP-verified signup and log the user in
**Request Body**:
```json
{
  "username": "string",
  "email": "string",
  "password": "string",
  "otp": "123456"
}
```
**Response**: UserDTO with `token` set. Also sets the JWT cookie.

#### POST /auth/login
**Description**: User login
**Request Body**:
```json
{
  "username": "string",
  "password": "string"
}
```
**Response**: a flat UserDTO — there is no `{user, token}` wrapper.
```json
{
  "id": 1,
  "username": "string",
  "email": "string",
  "displayName": null,
  "bio": null,
  "googleId": null,
  "authProvider": "EMAIL",
  "online": false,
  "token": "eyJhbGciOiJIUzM4NCJ9..."
}
```
**Note**: Also sets JWT cookie automatically

#### POST /auth/email-otp/send
**Description**: Send a 6-digit OTP to an email address (creates the user if it
does not exist). Rate limited to 3 requests per 10 minutes per email.
**Request Body**:
```json
{ "email": "string" }
```

#### POST /auth/email-otp/verify
**Description**: Verify the emailed OTP; issues a JWT cookie and returns UserDTO

#### POST /auth/google/login
**Description**: Exchange a Google ID token for a session; issues a JWT cookie
and returns UserDTO
**Request Body**:
```json
{ "idToken": "string" }
```

#### POST /auth/logout
**Description**: User logout — clears the `JWT` cookie (maxAge 0)
**Response**:
```json
"Logged out successfully"
```

#### GET /auth/getonlineusers
**Description**: Get usernames of currently online users
**Response**:
```json
["alice", "bob"]
```

#### GET /auth/getcurrentuser
**Description**: Get current authenticated user details
**Response**: UserDTO (see Data Models)

### Profile Endpoints

#### GET /api/profile
**Description**: Current user's profile

#### PUT /api/profile
**Description**: Update `displayName`, `bio`, `username`

#### POST /api/profile/link-email/send
**Description**: Send an OTP to an email address to link it to the account

#### POST /api/profile/link-email/verify
**Description**: Verify the OTP and link the email

#### POST /api/profile/link-google
**Description**: Link a Google account by verifying its ID token

#### POST /api/profile/unlink-email
#### POST /api/profile/unlink-google
**Description**: Remove an auth method (refused if it is the only one left)

### Friend Endpoints

#### GET /api/friends
**Description**: List accepted friends

#### GET /api/friends/requests/incoming
#### GET /api/friends/requests/rejected
**Description**: List pending incoming / rejected friend requests

#### GET /api/friends/search
**Description**: Search users by username
**Query Parameters**:
- `q`: search query

#### POST /api/friends/request
**Description**: Send a friend request

#### POST /api/friends/accept/{id}
#### POST /api/friends/reject/{id}
**Description**: Accept / reject an incoming request

#### DELETE /api/friends/cancel/{id}
**Description**: Cancel an outgoing request

#### DELETE /api/friends/{id}
**Description**: Remove a friend

### Conversation Endpoints (DMs)

#### GET /api/conversations
**Description**: List the current user's active conversations
**Response**: array of ConversationDTO

#### GET /api/conversations/{id}/messages
**Description**: Paginated message history for a conversation
**Query Parameters**:
- `page`: page index (default `0`)
- `size`: page size (default `50`)
**Response**: a Spring `Page<DirectMessageDTO>` (`content`, `totalElements`,
`totalPages`, …)

#### PUT /api/conversations/{id}/retention
**Description**: Update the conversation retention policy; also pushes a
`RETENTION_POLICY_UPDATE:<POLICY>` notification to both participants' DM queues
**Request Body**:
```json
{ "policy": "SIX_HOURS|ONE_DAY|SEVEN_DAYS" }
```

#### POST /api/conversations/with/{username}
**Description**: Get or create a conversation with a friend

### Channel Endpoints

#### GET /api/channels
**Description**: List the channels the current user belongs to

#### POST /api/channels
**Description**: Create a channel (creator becomes owner)
**Request Body**:
```json
{ "name": "string", "description": "string" }
```

#### POST /api/channels/join
**Description**: Join a channel with an invite code
**Request Body**:
```json
{ "inviteCode": "string" }
```

#### DELETE /api/channels/{id}/leave
**Description**: Leave a channel

#### GET /api/channels/{id}/messages
**Description**: CHAT history for a channel the user belongs to

### Call Endpoints

All of these are members-only and return `403` otherwise.

#### GET /api/channels/{channelId}/call-token
**Description**: Mint a LiveKit token for the channel's call
**Response**:
```json
{
  "token": "string",
  "url": "wss://...",
  "room": "channelId",
  "identity": "userId"
}
```

#### GET /api/channels/{channelId}/call-status
**Description**: How many participants are currently in the channel's call

#### POST /api/channels/{channelId}/transcribe
**Description**: Transcribe a hosted recording via Deepgram and persist it
**Request Body**:
```json
{ "audioUrl": "string" }
```

#### POST /api/channels/{channelId}/recording
**Description**: Upload a browser-captured recording (multipart, field `file`),
transcribe the bytes and persist the result

#### GET /api/channels/{channelId}/transcripts
**Description**: Saved call transcripts for the channel, newest first

### AI Memory Endpoints

#### GET /api/channels/{channelId}/ask
**Description**: RAG answer over the channel's memory. Members only.
**Query Parameters**:
- `q`: the question
**Response**:
```json
{ "answer": "string", "sourceIds": [1, 2] }
```

#### GET /api/channels/{channelId}/decisions
**Description**: Extracted decisions for the channel (active + superseded),
newest first. Members only.

See [RAG_API_DOCS.md](RAG_API_DOCS.md) for the full AI surface.

### Eval Endpoints (non-product)

Registered only when `recall.eval.enabled=true`. These ship with the eval
harness, not the product; request bodies are plain maps.

#### POST /api/eval/channels/{channelId}/message
**Request Body**: `{ "content": "string" }`

#### POST /api/eval/channels/{channelId}/transcript
**Request Body**: `{ "content": "string" }`

#### GET /api/eval/channels/{channelId}/memory-count
**Description**: Vectors stored so far (ingestion is async — poll this)

#### GET /api/eval/channels/{channelId}/ask
**Description**: Same RAG call as the product endpoint, but returns the
retrieved chunks and retrieval mode too
**Query Parameters**:
- `q`: the question

---

## WebSocket Configuration

### Connection Endpoint
```
ws://localhost:8080/ws
```
(SockJS fallback enabled.)

### Allowed Origins
Read from the `allowed-origins` property (env `ALLOWED_ORIGINS`), default
`https://echomessaging.duckdns.org,http://localhost:5173`.

### Message Types
```
ChatMessage.MessageType     JOIN, LEAVE          (presence only, /topic/public)
ChannelMessage.MessageType  CHAT, JOIN, LEAVE, TYPING  (channel chat)
```

---

## WebSocket Endpoints (via STOMP)

### Subscribe to Presence Events
**Topic**: `/topic/public`
**Description**: Subscribe to receive user join/leave notifications
**Receives**:
```json
{
  "sender": "username",
  "content": "",
  "color": null,
  "timestamp": "2025-09-28T10:30:00",
  "type": "JOIN|LEAVE"
}
```

### Add User to Chat
**Destination**: `/app/chat.addUser`
**Description**: Register presence for the current session (marks user as online)
**Payload**:
```json
{
  "sender": "username"
}
```
**Note**: `type` in the payload is ignored — the server always sets `JOIN`.
Nothing is broadcast if the sender does not exist or the session was already
registered.

### Subscribe to Direct Messages
**Topic**: `/user/{username}/queue/dm`
**Description**: Receives DirectMessageDTOs for the user (both sides of a
conversation get the message), plus typing and retention notifications

### Send Direct Message
**Destination**: `/app/dm.sendMessage`
**Description**: Send a DM. Provide either `conversationId` or
`recipientUsername`.
**Payload** (DirectMessageRequestDTO):
```json
{
  "conversationId": 1,
  "senderUsername": "sender",
  "recipientUsername": "recipient",
  "content": "Hello!",
  "type": "MESSAGE"
}
```

### Send Typing Indicator
**Destination**: `/app/dm.typing`
**Description**: Same payload as above with `type: "TYPING"`; delivered to the
other participant's `/user/{username}/queue/dm`.

### Subscribe to Channel Messages
**Topic**: `/topic/channel/{channelId}`
**Description**: Receives ChannelMessageDTOs broadcast to the channel

### Send Channel Message
**Destination**: `/app/channel/{channelId}/send`
**Payload** (ChannelMessageRequestDTO):
```json
{
  "sender": "username",
  "content": "Hello channel!",
  "color": "#aabbcc",
  "type": "CHAT"
}
```
**Note**: only `CHAT` messages are persisted; `TYPING`/`JOIN`/`LEAVE` are
broadcast only.

---

## Data Models

### User (UserDTO)
```json
{
  "id": "Long",
  "username": "String",
  "email": "String",
  "displayName": "String",
  "bio": "String",
  "googleId": "String (\"connected\" or null)",
  "authProvider": "String (EMAIL|GOOGLE)",
  "online": "Boolean (never set — always false; use GET /auth/getonlineusers)",
  "token": "String (set on login paths only)"
}
```

### ChatMessage
Transient presence payload — not persisted.
```json
{
  "sender": "String",
  "content": "String",
  "color": "String",
  "timestamp": "LocalDateTime",
  "type": "JOIN|LEAVE"
}
```

---

## CORS Configuration
- **Allowed Origins**: from `allowed-origins` (env `ALLOWED_ORIGINS`), default
  `https://echomessaging.duckdns.org,http://localhost:5173`
- **Credentials**: Allowed
- **Methods**: GET, POST, PUT, DELETE, OPTIONS

---

## Status Codes

### Success
- `200 OK` - Request successful
- `201 Created` - Resource created

### Client Errors  
- `400 Bad Request` - Invalid request data
- `401 Unauthorized` - Authentication required
- `403 Forbidden` - Access denied
- `404 Not Found` - Resource not found
- `429 Too Many Requests` - OTP rate limit hit

### Server Errors
- `500 Internal Server Error` - Server error

---

## Example Frontend Integration

### Login Flow
```javascript
// 1. Login
const loginResponse = await fetch('/auth/login', {
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({ username: 'user', password: 'pass' })
});
const user = await loginResponse.json();
const token = user.token;

// 2. Store token for subsequent requests
localStorage.setItem('token', token);

// 3. Connect to WebSocket
const socket = new SockJS('/ws');
const stompClient = Stomp.over(socket);
stompClient.connect({}, function() {
  // Subscribe to presence events
  stompClient.subscribe('/topic/public', function(message) {
    const chatMessage = JSON.parse(message.body);
    displayPresenceEvent(chatMessage);
  });
  
  // Subscribe to direct messages
  stompClient.subscribe(`/user/${user.username}/queue/dm`, function(message) {
    const directMessage = JSON.parse(message.body);
    displayDirectMessage(directMessage);
  });
  
  // Register presence
  stompClient.send('/app/chat.addUser', {}, JSON.stringify({
    sender: user.username
  }));
});
```

### Authenticated Requests
```javascript
// Include token in requests
const response = await fetch('/auth/getonlineusers', {
  headers: {
    'Authorization': `Bearer ${localStorage.getItem('token')}`
  }
});
```
