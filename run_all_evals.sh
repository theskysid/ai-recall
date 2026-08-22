#!/bin/bash
set -e

# Run demote
sed -i 's/RECALL_RETRIEVAL_MODE=.*/RECALL_RETRIEVAL_MODE=demote/' .env
docker compose up -d app
while ! curl -s -f http://localhost:8080/actuator/health >/dev/null; do sleep 2; done
RECALL_USER=eval_checker RECALL_PASS=password python3 ai/eval/run_eval.py ask --out results-demote.json

# Run filter
sed -i 's/RECALL_RETRIEVAL_MODE=.*/RECALL_RETRIEVAL_MODE=filter/' .env
docker compose up -d app
while ! curl -s -f http://localhost:8080/actuator/health >/dev/null; do sleep 2; done
RECALL_USER=eval_checker RECALL_PASS=password python3 ai/eval/run_eval.py ask --out results-filter.json

echo "All asks complete."
# Run report
python3 ai/eval/run_eval.py report results-*.json
