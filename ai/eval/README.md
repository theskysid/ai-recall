# Staleness evaluation — result

This experiment has been **run and closed**. The harness that produced it
(`run_eval.py`, `corpus.json`, `seed-state.json`, the `/api/eval/**` seeding
endpoints, and the `RECALL_RETRIEVAL_MODE` switch) has been removed from the
codebase; what remains here is the record.

The dataset and method are in [staleness-dataset.md](staleness-dataset.md).
The raw per-query output of each arm is in `results-baseline.json`,
`results-demote.json` and `results-filter.json`.

## What it measured

Whether excluding superseded decisions from retrieval stops the system
answering with a decision the team already reversed. Three arms, identical in
everything but one clause of one SQL query:

| Arm | Superseded decisions | Outcome |
|---|---|---|
| A `baseline` | ignored — plain cosine similarity | control |
| B `demote` | +10 distance penalty, still eligible | still backfills stale rows in a sparse channel |
| C `filter` | excluded by `WHERE`, never retrieved | **shipped** |

`filter` is now the only behaviour: `MemoryVectorRepository.findTop5ActiveOnly`
is the sole retrieval query, and `RagService` calls it unconditionally. The two
losing queries and the mode switch are gone — the comparison is settled, and a
config knob whose other settings are known to be worse is not a knob.

## Reading the results files

`stale_chunk_retrieved` is the trustworthy number: an exact string match asking
whether the superseded chunk reached the prompt. No model judgement involved,
so it stands on its own as the retrieval-layer result.

`auto_label` is keyword triage for sorting rows, not ground truth — it cannot
judge stance, and "no, we left MongoDB" contains the stale keyword. Every row
keeps its raw `answer` and an empty `human_label`. Hand-label before quoting any
answer-quality percentage.

## Caveat that shaped the numbers

Groq's free tier allows 30 requests/minute, and one seeded message cost up to
four calls. A rate-limited extraction was originally filed as "not a decision",
indistinguishable from a genuine one — the first seeding attempt hit the limit
within 60 seconds and produced 0/5 correct supersessions with nothing surfaced
to the caller. The corpus looked fine and was worthless.

That is why `DecisionService` now returns a three-way result with `LLM_ERROR`
distinct from `NOT_A_DECISION`, counts the two stages' failures separately, and
logs them under `DECISION_EXTRACTION_LLM_ERROR` / `CONFLICT_CLASSIFICATION_LLM_ERROR`.
`RagService` still falls back to raw context when answer generation fails, which
is correct for the UI but would look like an answer in any future results file —
worth re-checking if this experiment is ever rerun.

## If you rerun this

You would need to reinstate a seeding path: chat arrives over STOMP and
transcripts only ever come back from Deepgram, so neither can be seeded over
HTTP without one. Seed through the real ingestion path (same embedder, same
extractor, same supersession check) rather than writing `memory_vectors`
directly — writing rows directly assumes away the behaviour under test.
