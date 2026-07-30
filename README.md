# 📨 Echo Messaging

![Java](https://img.shields.io/badge/Java_21-ED8B00?style=flat&logo=openjdk&logoColor=white)
![Spring Boot](https://img.shields.io/badge/Spring_Boot-6DB33F?style=flat&logo=springboot&logoColor=white)
![React](https://img.shields.io/badge/React-61DAFB?style=flat&logo=react&logoColor=black)
![Vite](https://img.shields.io/badge/Vite-646CFF?style=flat&logo=vite&logoColor=white)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL_16-4169E1?style=flat&logo=postgresql&logoColor=white)
![Docker](https://img.shields.io/badge/Docker-2496ED?style=flat&logo=docker&logoColor=white)
![GitHub Actions](https://img.shields.io/badge/GitHub_Actions-2088FF?style=flat&logo=githubactions&logoColor=white)

A real-time chat application with public group messaging, private conversations, and multi-method authentication. Built with Spring Boot and React, connected over WebSocket (STOMP/SockJS), and deployed via CI/CD pipeline to AWS EC2.

---

## 🛠️ Tech Stack

| Layer        | Technologies                                             |
|--------------|----------------------------------------------------------|
| **Backend**  | Spring Boot, Java 21, JPA, WebSocket (STOMP/SockJS), JWT |
| **Frontend** | React, Vite, Axios, SockJS, STOMP.js                     |
| **Database** | PostgreSQL 16                                            |
| **Auth**     | Password, Email OTP, Phone OTP (Twilio), Google OAuth2   |
| **Infra**    | Docker, Docker Compose, GitHub Actions, AWS EC2          |

---

## ✨ Features

- **Public group chat** with real-time typing indicators
- **Private messaging** between users
- **Online user list** updated in real time
- **Multi-auth** — password, email OTP, phone OTP, Google OAuth2

---

## 🚀 Local Setup

### Prerequisites

- Docker & Docker Compose

### Run

```bash
git clone https://github.com/theskysid/echo-messaging.git
cd echo-messaging
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

---

## 🔐 Environment Variables

Create a `.env` file in the project root with the following:

```env
# ── Database ──────────────────────────────────
DB_NAME=echo                          # PostgreSQL database name
DB_USER=postgres                      # PostgreSQL username
DB_PASSWORD=                          # PostgreSQL password
SPRING_DATASOURCE_URL=jdbc:postgresql://postgres:5432/echo  # JDBC URL (use container hostname)

# ── Auth / JWT ────────────────────────────────
JWT_SECRET=                           # Secret key for signing JWT tokens
JWT_EXPIRATION=3600000                # Token TTL in ms (default: 1 hour)

# ── CORS ──────────────────────────────────────
ALLOWED_ORIGINS=http://localhost:5173 # Comma-separated allowed origins

# ── Twilio (Phone OTP) ───────────────────────
TWILIO_ACCOUNT_SID=                   # Twilio account SID
TWILIO_AUTH_TOKEN=                    # Twilio auth token
TWILIO_PHONE_NUMBER=                  # Twilio sender phone number

# ── Google OAuth2 ─────────────────────────────
GOOGLE_CLIENT_ID=                     # Google OAuth2 client ID

# ── Email OTP (SMTP) ─────────────────────────
MAIL_HOST=smtp.gmail.com              # SMTP host
MAIL_PORT=587                         # SMTP port
MAIL_USERNAME=                        # SMTP username / email
MAIL_PASSWORD=                        # SMTP app password

# ── Frontend ─────────────────────────────────
VITE_API_URL=http://localhost:8080    # Backend API URL injected at build
VITE_GOOGLE_CLIENT_ID=                # Google client ID for frontend
```

---

## 📁 Project Structure

```
echo-messaging/
├── echo-backend/
│   ├── src/                    # Java source & resources
│   ├── Dockerfile
│   ├── pom.xml
│   └── API_DOCS.md
├── echo-frontend/
│   ├── src/                    # React components, pages, services
│   ├── Dockerfile
│   ├── nginx.conf
│   ├── vite.config.js
│   └── package.json
├── .github/
│   └── workflows/
│       └── deploy.yml          # CI/CD pipeline
├── docker-compose.yml          # Production compose
├── docker-compose.local.yml    # Local development compose
├── .env                        # Environment variables (not committed)
└── README.md
```

---

## 🔄 CI/CD Pipeline

Automated via GitHub Actions (`deploy.yml`). Pushes to `main` trigger build and deploy.

```
┌──────────────┐     ┌───────────────────┐     ┌────────────────────────┐
│  Push to     │────▶│  Build & Push     │────▶│  Deploy to Production  │
│  main        │     │  Docker Images    │     │  EC2 (Amazon Linux)    │
└──────────────┘     └───────────────────┘     └────────────────────────┘
```

| Branch | Environment | EC2 OS            | Backend Port | Frontend Port | URL                               |
|--------|-------------|-------------------|:------------:|:-------------:|:----------------------------------|
| `main` | Production  | Amazon Linux 2023 |     8080     |     5173      | https://echomessaging.duckdns.org |

**Flow:**

1. **Build** — Docker images tagged `latest`, pushed to Docker Hub
2. **Deploy** — SSH into EC2, pull images, recreate containers with `docker compose`
3. **Verify** — Health check via `/actuator/health`

---

## 📡 API Endpoints

### REST

| Method | Endpoint                   | Auth | Description                    |
|--------|----------------------------|------|--------------------------------|
| POST   | `/api/auth/register`       | No   | Register a new user            |
| POST   | `/api/auth/login`          | No   | Login (returns JWT)            |
| POST   | `/api/auth/logout`         | Yes  | Logout current user            |
| GET    | `/api/auth/getcurrentuser` | Yes  | Get authenticated user details |
| GET    | `/api/auth/getonlineusers` | Yes  | List online users              |
| GET    | `/api/messages/public`     | Yes  | Fetch public chat history      |
| GET    | `/api/messages/private`    | Yes  | Fetch private message history  |

### WebSocket (STOMP over SockJS)

| Type      | Destination / Topic              | Description               |
|-----------|----------------------------------|---------------------------|
| Connect   | `/ws`                            | SockJS handshake endpoint |
| Subscribe | `/topic/public`                  | Public messages stream    |
| Subscribe | `/user/{username}/queue/private` | Private messages stream   |
| Send      | `/app/chat.sendMessage`          | Send public message       |
| Send      | `/app/chat.sendPrivateMessage`   | Send private message      |
| Send      | `/app/chat.addUser`              | Join chat (go online)     |

> Full API documentation: [`echo-backend/API_DOCS.md`](echo-backend/API_DOCS.md)

---

## 📄 License

This project is unlicensed — all rights reserved.
