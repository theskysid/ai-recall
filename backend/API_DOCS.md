# Echo Messaging Backend API Documentation

## Base URL
```
http://localhost:8080
```

## Authentication
Most endpoints require JWT authentication. Include the token in one of these ways:
- **Header**: `Authorization: Bearer <token>`
- **Cookie**: `JWT=<token>` (automatically set after login)

Token expires after 1 hour.

---

## REST API Endpoints

### Authentication Endpoints

#### POST /api/auth/register
**Description**: Register a new user
**Authentication**: Not required
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
  "online": false
}
```

#### POST /api/auth/login
**Description**: User login
**Authentication**: Not required
**Request Body**:
```json
{
  "username": "string",
  "password": "string"
}
```
**Response**:
```json
{
  "user": {
    "id": 1,
    "username": "string",
    "email": "string",
    "online": false
  },
  "token": "eyJhbGciOiJIUzM4NCJ9..."
}
```
**Note**: Also sets JWT cookie automatically

#### POST /api/auth/logout
**Description**: User logout
**Authentication**: Required
**Response**: 
```json
"User logged out successfully"
```

#### GET /api/auth/getonlineusers
**Description**: Get list of currently online users
**Authentication**: Required
**Response**:
```json
{
  "onlineUsers": [
    {
      "id": 1,
      "username": "string",
      "email": "string",
      "online": true
    }
  ]
}
```

#### GET /api/auth/getcurrentuser
**Description**: Get current authenticated user details
**Authentication**: Required
**Response**:
```json
{
  "id": 1,
  "username": "string",
  "email": "string",
  "online": true
}
```

### Message Endpoints

#### GET /api/messages/private
**Description**: Get private messages between two users
**Authentication**: Required
**Query Parameters**:
- `user1`: Username of first user
- `user2`: Username of second user
**Response**:
```json
[
  {
    "id": 1,
    "content": "Hello!",
    "sender": "user1",
    "recipient": "user2",
    "timestamp": "2025-09-28T10:30:00",
    "type": "PRIVATE_MESSAGE"
  }
]
```

#### GET /api/messages/public
**Description**: Get all public chat messages
**Authentication**: Required
**Response**:
```json
[
  {
    "id": 1,
    "content": "Hello everyone!",
    "sender": "username",
    "timestamp": "2025-09-28T10:30:00",
    "type": "CHAT"
  }
]
```

---

## WebSocket Configuration

### Connection Endpoint
```
ws://localhost:8080/ws
```

### Allowed Origins
- `http://localhost:5176`
- `http://localhost:5174`

### Message Types
```
CHAT - Public chat message
JOIN - User joined chat
LEAVE - User left chat
PRIVATE_MESSAGE - Private message between users
```

---

## WebSocket Endpoints (via STOMP)

### Subscribe to Public Messages
**Topic**: `/topic/public`
**Description**: Subscribe to receive all public chat messages and user join/leave notifications
**Receives**:
```json
{
  "id": 1,
  "content": "message content",
  "sender": "username",
  "timestamp": "2025-09-28T10:30:00",
  "type": "CHAT|JOIN|LEAVE"
}
```

### Subscribe to Private Messages
**Topic**: `/user/{username}/queue/private`
**Description**: Subscribe to receive private messages for specific user
**Receives**:
```json
{
  "id": 1,
  "content": "private message",
  "sender": "sender_username",
  "recipient": "recipient_username",
  "timestamp": "2025-09-28T10:30:00",
  "type": "PRIVATE_MESSAGE"
}
```

### Send Public Message
**Destination**: `/app/chat.sendMessage`
**Description**: Send a message to public chat
**Payload**:
```json
{
  "content": "Hello everyone!",
  "sender": "username",
  "type": "CHAT"
}
```

### Add User to Chat
**Destination**: `/app/chat.addUser`
**Description**: Add user to chat (marks user as online)
**Payload**:
```json
{
  "sender": "username",
  "type": "JOIN"
}
```

### Send Private Message
**Destination**: `/app/chat.sendPrivateMessage`
**Description**: Send a private message to specific user
**Payload**:
```json
{
  "content": "Private message",
  "sender": "sender_username",
  "recipient": "recipient_username",
  "type": "PRIVATE_MESSAGE"
}
```

---

## Data Models

### User
```json
{
  "id": "Long",
  "username": "String",
  "email": "String", 
  "online": "Boolean"
}
```

### ChatMessage
```json
{
  "id": "Long",
  "content": "String",
  "sender": "String",
  "recipient": "String (optional - for private messages)",
  "timestamp": "LocalDateTime",
  "type": "MessageType"
}
```

### MessageType Enum
- `CHAT` - Public chat message
- `JOIN` - User joined notification
- `LEAVE` - User left notification  
- `PRIVATE_MESSAGE` - Private message

---

## CORS Configuration
- **Allowed Origins**: `http://localhost:5176`, `http://localhost:5174`
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

### Server Errors
- `500 Internal Server Error` - Server error

---

## Example Frontend Integration

### Login Flow
```javascript
// 1. Login
const loginResponse = await fetch('/api/auth/login', {
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({ username: 'user', password: 'pass' })
});
const { user, token } = await loginResponse.json();

// 2. Store token for subsequent requests
localStorage.setItem('token', token);

// 3. Connect to WebSocket
const socket = new SockJS('/ws');
const stompClient = Stomp.over(socket);
stompClient.connect({}, function() {
  // Subscribe to public messages
  stompClient.subscribe('/topic/public', function(message) {
    const chatMessage = JSON.parse(message.body);
    displayMessage(chatMessage);
  });
  
  // Subscribe to private messages
  stompClient.subscribe(`/user/${user.username}/queue/private`, function(message) {
    const privateMessage = JSON.parse(message.body);
    displayPrivateMessage(privateMessage);
  });
  
  // Add user to chat
  stompClient.send('/app/chat.addUser', {}, JSON.stringify({
    sender: user.username,
    type: 'JOIN'
  }));
});
```

### Authenticated Requests
```javascript
// Include token in requests
const response = await fetch('/api/auth/getonlineusers', {
  headers: {
    'Authorization': `Bearer ${localStorage.getItem('token')}`
  }
});
```

---

## Known Issues & Fixes Needed

1. **WebSocket CORS**: Add `http://localhost:5174` to allowed origins in WebSocketConfig.java
2. **JWT Token Handling**: Ensure frontend properly stores and sends JWT tokens
3. **Token Expiration**: Handle token refresh or re-authentication after 1 hour

---

## Database Tables

### users
- `id` (Primary Key)
- `username` (Unique)
- `email` 
- `password` (Encrypted)
- `is_online` (Boolean)

### chat_messages  
- `id` (Primary Key)
- `content`
- `sender` 
- `recipient` (Nullable - for private messages)
- `timestamp`
- `type` (Enum)

---

*Last Updated: September 28, 2025*
