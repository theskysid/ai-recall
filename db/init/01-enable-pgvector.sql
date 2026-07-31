-- Enable pgvector for the memory_vectors table (embeddings similarity search).
-- Runs automatically on first DB init (empty volume). For an existing volume,
-- run once manually:
--   docker compose -f docker-compose.local.yml exec postgres \
--     psql -U "$DB_USER" -d "$DB_NAME" -c "CREATE EXTENSION IF NOT EXISTS vector;"
CREATE EXTENSION IF NOT EXISTS vector;
