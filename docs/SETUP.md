# Setup and Execution

## Prerequisites
- Docker & Docker Compose
- Java 21 SDK (for local development)
- Node.js & npm (for frontend development)

## Required Environment Variables
The application requires a `.env` file to be present in the project root. Use `.env.example` as a template.

### Services Needing External Accounts:
1. **LiveKit Cloud**: Requires `LIVEKIT_URL`, `LIVEKIT_API_KEY`, `LIVEKIT_API_SECRET`.
2. **Deepgram**: Requires `DEEPGRAM_API_KEY` for audio transcription.
3. **Groq**: Requires `GROQ_API_KEY` to run the Llama models for synthesis and decision extraction.
4. **Google OAuth**: Requires `GOOGLE_CLIENT_ID` for authentication.
5. **SMTP Mail Provider**: Requires `MAIL_HOST`, `MAIL_USERNAME`, `MAIL_PASSWORD` for Email OTP functionalities.

*Note: Embeddings use local `all-MiniLM-L6-v2` via langchain4j and do not require an API key.*

## Running Locally

### 1. Full Stack (Dockerized)
The easiest way to run both backend and frontend, along with the Postgres database:
```bash
docker compose -f docker-compose.local.yml up --build
```
- Frontend will be available at: http://localhost:5173
- Backend will be available at: http://localhost:8080
- Postgres is exposed on port: 5433

### 2. Backend Only (Local Dev)
```bash
cd backend
./mvnw clean package    # Build and run tests
./mvnw spring-boot:run  # Run backend application
```
*Note: Make sure the postgres database is running before starting the backend locally.*

### 3. Frontend Only (Local Dev)
```bash
cd frontend
npm install
npm run dev             # Starts the Vite dev server
```
