# 🧠 Recall

![Java](https://img.shields.io/badge/Java_21-ED8B00?style=flat&logo=openjdk&logoColor=white)
![Spring Boot](https://img.shields.io/badge/Spring_Boot-6DB33F?style=flat&logo=springboot&logoColor=white)
![React](https://img.shields.io/badge/React-61DAFB?style=flat&logo=react&logoColor=black)
![Vite](https://img.shields.io/badge/Vite-646CFF?style=flat&logo=vite&logoColor=white)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL_16_+_pgvector-4169E1?style=flat&logo=postgresql&logoColor=white)
![Docker](https://img.shields.io/badge/Docker-2496ED?style=flat&logo=docker&logoColor=white)
![GitHub Actions](https://img.shields.io/badge/GitHub_Actions-2088FF?style=flat&logo=githubactions&logoColor=white)

A real-time channel app with an AI memory: text chat, LiveKit video calls, Deepgram transcription, and RAG Q&A over a per-channel vector memory — alongside friend DMs and presence. Built with Spring Boot and React, connected over WebSocket (STOMP/SockJS), and deployed via CI/CD pipeline to AWS EC2.

> The product is **Recall**; the codebase is still named `echo-*` (Java package `com.theskysid.echobackend`, Docker images `echo-messaging-*`).

---

## 🛠️ Tech Stack

| Layer        | Technologies                                                          |
|--------------|-----------------------------------------------------------------------|
| **Backend**  | Spring Boot, Java 21, JPA, WebSocket (STOMP/SockJS), JWT              |
| **Frontend** | React 19, Vite, Tailwind v4, React Router 7, Axios, SockJS, STOMP.js  |
| **Database** | PostgreSQL 16 + pgvector                                              |
| **AI**       | langchain4j (local MiniLM embeddings), Groq (Llama 3) for synthesis   |
| **Calls**    | LiveKit (audio/video), Deepgram (transcription)                       |
| **Auth**     | Password, Email OTP, Google OAuth2                                    |
| **Infra**    | Docker, Docker Compose, Caddy (Let's Encrypt), GitHub Actions, AWS EC2 |

---

## ✨ Features

- **Channels** — group text chat with typing indicators, LiveKit audio/video calls, Deepgram transcription of call recordings
- **Channel memory** — messages and transcripts embedded locally (MiniLM) into pgvector; ask a question and get a Groq-synthesised answer from the channel's own history
- **Decision extraction** — decisions pulled out of channel history, supersession-aware so a reversed decision stops being retrieved
- **Friend DMs** — direct messages with real-time typing indicators and per-conversation retention settings
- **Online user list** updated in real time
- **Multi-auth** — password, email OTP, Google OAuth2

---

## 🚀 Local Setup

### Prerequisites

- Docker & Docker Compose

### Run

```bash
git clone https://github.com/theskysid/ai-recall.git
cd ai-recall
```

Create a `.env` file in the project root (see [Environment Variables](#-environment-variables)), then:

```bash
docker compose -f docker-compose.local.yml up --build
```

| Service    | URL                   |
|------------|-----------------------|
| Frontend   | http://localhost:5173 |
| Backend    | http://localhost:8080 |
| PostgreSQL | localhost:5433        |

> **pgvector:** the extension is created automatically on a *fresh* database volume by
> `db/init/01-enable-pgvector.sql`. On an existing `postgres_data` volume, run it once:
>
> ```bash
> docker compose -f docker-compose.local.yml exec postgres \
>   psql -U "$DB_USER" -d "$DB_NAME" -c "CREATE EXTENSION IF NOT EXISTS vector;"
> ```

### Without Docker

Postgres (with pgvector) still has to be running and reachable at `SPRING_DATASOURCE_URL`.

```bash
cd backend
./mvnw spring-boot:run     # run locally
./mvnw test                # tests only

cd frontend
npm install
npm run dev                # dev server (Vite)
npm run lint               # ESLint
npm run build              # production build
```

---

## 🔐 Environment Variables

```bash
cp .env.example .env
```

`.env.example` documents every variable and ships working defaults for the rest; these are the ones that need real values:

| Variable                                              | Needed for                              |
|-------------------------------------------------------|-----------------------------------------|
| `DB_PASSWORD`                                          | PostgreSQL                              |
| `JWT_SECRET`                                           | Signing JWT tokens                      |
| `GOOGLE_CLIENT_ID`, `VITE_GOOGLE_CLIENT_ID`            | Google OAuth2 sign-in                   |
| `MAIL_USERNAME`, `MAIL_PASSWORD`                       | Email OTP (SMTP)                        |
| `LIVEKIT_URL`, `LIVEKIT_API_KEY`, `LIVEKIT_API_SECRET` | Channel audio/video calls               |
| `DEEPGRAM_API_KEY`                                     | Call transcription                      |
| `GROQ_API_KEY`                                         | RAG answers + decision extraction       |
| `DOMAIN`                                               | Caddy TLS certificate (production only) |

Embeddings run in-process (all-MiniLM-L6-v2 via langchain4j) and need no key. Leave anything
you don't have blank — that feature is simply inactive.

`RECALL_RETRIEVAL_MODE=filter` and `RECALL_EVAL_ENABLED=false` are evaluation-harness knobs;
leave them alone unless you are running the harness (see [ai/eval/README.md](ai/eval/README.md)).

---

## 📁 Project Structure

```
ai-recall/
├── backend/
│   ├── src/                    # Java source & resources
│   ├── Dockerfile
│   ├── pom.xml
│   ├── API_DOCS.md
│   └── RAG_API_DOCS.md         # AI / memory endpoints
├── frontend/
│   ├── src/                    # React components, pages, services
│   ├── Dockerfile
│   ├── nginx.conf
│   ├── vite.config.js
│   └── package.json
├── ai/                         # project, architecture & convention docs
│   └── eval/                   # retrieval evaluation harness
├── db/
│   └── init/
│       └── 01-enable-pgvector.sql  # runs on first DB init
├── .github/
│   └── workflows/
│       └── deploy.yml          # CI/CD pipeline
├── Caddyfile                   # production reverse proxy + TLS
├── docker-compose.yml          # Production compose
├── docker-compose.local.yml    # Local development compose
├── .env.example                # Documented template
├── .env                        # Environment variables (not committed)
├── PRODUCT.md
├── CLAUDE.md
└── README.md
```

---

## 🔄 CI/CD Pipeline

Automated via GitHub Actions (`deploy.yml`). Pushes to `main` trigger lint, build and deploy.

```
┌──────────────┐     ┌───────────────────┐     ┌────────────────────────┐
│  Push to     │────▶│  Build & Push     │────▶│  Deploy to Production  │
│  main        │     │  Docker Images    │     │  EC2 (Amazon Linux)    │
└──────────────┘     └───────────────────┘     └────────────────────────┘
```

| Branch | Environment | EC2 OS            |    Public Ports    | URL                               |
|--------|-------------|-------------------|:------------------:|:----------------------------------|
| `main` | Production  | Amazon Linux 2023 | 80 / 443  (Caddy)  | https://echomessaging.duckdns.org |

Caddy is the only ingress; the backend (`8080`) and PostgreSQL (`5433`) are bound to loopback only.

**Flow:**

1. **Lint** — `npm ci && npm run lint` in `frontend/`; deploy does not run unless this passes
2. **Build** — Docker images tagged with both the commit SHA and `latest`, pushed to Docker Hub
3. **Deploy** — SSH into EC2, regenerate `.env` with `APP_TAG=<sha>`, pull images, recreate containers
4. **Verify** — poll `/actuator/health` for up to 2 minutes; on failure `APP_TAG` is reset to the previous SHA and the stack rolled back. On success, images older than 168h are pruned.

---

## 📡 API Endpoints

### REST

| Method   | Endpoint                                            | Auth | Description                        |
|----------|-----------------------------------------------------|------|------------------------------------|
| POST     | `/auth/signup`                                       | No   | Register a new user                |
| POST     | `/auth/signup/verify`                                | No   | Verify the signup OTP              |
| POST     | `/auth/login`                                        | No   | Login (returns JWT)                |
| POST     | `/auth/logout`                                       | Yes  | Logout current user                |
| GET      | `/auth/getcurrentuser`                               | Yes  | Get authenticated user details     |
| GET      | `/auth/getonlineusers`                               | Yes  | List online users                  |
| POST     | `/auth/email-otp/send`, `/auth/email-otp/verify`     | No   | Email OTP login                    |
| POST     | `/auth/google/login`                                 | No   | Google OAuth2 login                |
| GET/POST | `/api/channels`                                      | Yes  | List / create channels             |
| POST     | `/api/channels/join`                                 | Yes  | Join a channel                     |
| DELETE   | `/api/channels/{id}/leave`                           | Yes  | Leave a channel                    |
| GET      | `/api/channels/{id}/messages`                        | Yes  | Channel message history            |
| GET      | `/api/channels/{id}/call-token`, `/call-status`      | Yes  | LiveKit token / current call state |
| POST     | `/api/channels/{id}/transcribe`, `/recording`        | Yes  | Upload call audio for transcription|
| GET      | `/api/channels/{id}/transcripts`                     | Yes  | Call transcripts                   |
| GET      | `/api/channels/{id}/ask`                             | Yes  | RAG answer over channel memory     |
| GET      | `/api/channels/{id}/decisions`                       | Yes  | Decisions extracted from a channel |
| GET      | `/api/conversations`                                 | Yes  | List DM conversations              |
| POST     | `/api/conversations/with/{username}`                 | Yes  | Open (or create) a DM              |
| GET      | `/api/conversations/{id}/messages`                   | Yes  | Fetch DM history                   |
| PUT      | `/api/conversations/{id}/retention`                  | Yes  | Set DM retention                   |
| —        | `/api/friends/**`                                    | Yes  | Friend list, requests, search      |
| —        | `/api/profile/**`                                    | Yes  | Profile, email/Google linking      |

> `/api/eval/**` exists only when `RECALL_EVAL_ENABLED=true` — see [ai/eval/README.md](ai/eval/README.md). Keep it false in production.

### WebSocket (STOMP over SockJS)

| Type      | Destination / Topic              | Description                       |
|-----------|----------------------------------|-----------------------------------|
| Connect   | `/ws`                            | SockJS handshake endpoint         |
| Subscribe | `/topic/public`                  | Presence / online-user JOIN events|
| Subscribe | `/topic/channel/{channelId}`     | Channel messages & typing         |
| Subscribe | `/user/{username}/queue/dm`      | Direct messages stream            |
| Send      | `/app/chat.addUser`              | Announce presence (go online)     |
| Send      | `/app/channel/{channelId}/send`  | Send channel message              |
| Send      | `/app/dm.sendMessage`            | Send direct message               |
| Send      | `/app/dm.typing`                 | DM typing indicator               |

> Full API documentation: [`backend/API_DOCS.md`](backend/API_DOCS.md) · AI/memory endpoints: [`backend/RAG_API_DOCS.md`](backend/RAG_API_DOCS.md)

---

## 📄 License

This project is unlicensed — all rights reserved.
