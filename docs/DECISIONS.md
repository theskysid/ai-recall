# Technical Decisions

- **Spring Boot + STOMP**: Provides robust Java backend infrastructure and standard WebSocket messaging support natively integrated with Spring's security model.
- **LiveKit + Deepgram behind swappable TranscriptionProvider**: Standardizes RTC calls and abstracts audio processing, allowing flexibility in transcription providers.
- **BGE-M3 (or local MiniLM)**: Ensures local, free, privacy-friendly embeddings that integrate cleanly in a Java runtime via ONNX/Langchain4j.
- **Single Postgres+pgvector store**: Eliminates the overhead of managing a separate standalone vector DB by using PostgreSQL's pgvector extension.
- **Groq-hosted LLM for dev**: Uses ultra-fast inference APIs for synthesis and decision extraction.
- **GitHub Actions with deploy deferred to Phase 7**: Focuses development efforts on building features locally before automating deployment pipelines.
