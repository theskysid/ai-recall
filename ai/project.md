# Project

**Echo Messaging** — a real-time chat application. Spring Boot + React,
connected over WebSocket (STOMP/SockJS), deployed via Docker to AWS EC2.

## Features today

- **Authentication** — password login/signup, email OTP, phone OTP (Twilio),
  Google OAuth2. Stateless JWT issued as an httpOnly cookie.
- **Global/public chat** — real-time group messaging with typing indicators,
  online-user list, JOIN/LEAVE events. Messages retained 7 days.
- **Private/direct messaging** — 1:1 messaging restricted to friends, with a
  per-conversation retention policy.
- **Friendship system** — search users, send/accept/reject/cancel requests,
  list friends, real-time friend events over WebSocket.
- **Channels (backend only)** — any user can create a channel (name +
  description), which auto-generates a unique shareable invite code; others
  join by code; members can leave; owner-leave transfers ownership to the
  longest-standing member (or deletes the empty channel). REST:
  create / join / leave / list. No channel messaging or UI yet.
- **User profiles** — display name, bio, profile fields.

## Planned next

- **Channel messaging** — STOMP topics + frontend for the new channel feature.
- **Audio/video calls** — LiveKit env vars are scaffolded in `.env`
  (`LIVEKIT_*`, `VITE_LIVEKIT_URL`); no backend/frontend code exists yet.
