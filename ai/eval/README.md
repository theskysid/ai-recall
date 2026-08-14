# Staleness evaluation — runbook

Operational notes for `run_eval.py`. The dataset itself, the metrics and the
paper-facing writeup live in [staleness-dataset.md](staleness-dataset.md);
the machine-readable corpus is [corpus.json](corpus.json).

## What this measures

Whether excluding superseded decisions from retrieval stops the system
answering with a decision the team already reversed. Three arms, identical in
everything but one clause of one SQL query:

| Arm | `RECALL_RETRIEVAL_MODE` | Superseded decisions |
|---|---|---|
| A | `baseline` | ignored — plain cosine similarity |
| B | `demote` | +10 distance penalty, still eligible |
| C | `filter` | excluded by `WHERE`, never retrieved |

## Prerequisites

In `.env`:

```
RECALL_EVAL_ENABLED=true
RECALL_RETRIEVAL_MODE=filter
```

`RECALL_EVAL_ENABLED` exposes `/api/eval/**`. Without it there is no way to
seed the corpus at all — chat arrives over STOMP and transcripts only ever come
back from Deepgram. **Keep it `false` in production.**

Changing either variable requires a container restart:

```bash
docker compose -f docker-compose.local.yml up -d --build app
```

## Speaker accounts

Seeding creates one account per speaker in `corpus.json` and joins each to the
channels they talk in. Accounts are created on demand via `/auth/signup` (no
email OTP) and reused across runs.

| Username | Email | Appears in |
|---|---|---|
| `priya` | priya@eval.local | S1 database, S4 auth, controls |
| `dev` | dev@eval.local | S1 database, S4 auth, controls |
| `marco` | marco@eval.local | S1 database, S4 auth, controls |
| `sam` | sam@eval.local | S2 deploy date, S5 scope |
| `ana` | ana@eval.local | S2 deploy date, S5 scope, controls |
| `ravi` | ravi@eval.local | S2 deploy date, S5 scope |
| `lena` | lena@eval.local | S3 budget |
| `tom` | tom@eval.local | S3 budget |

Password for all of them: `EvalSpeaker123` (constant `SPEAKER_PASSWORD`).

**This affects presentation only, not results.**
`MemoryIngestionService.ingestMessage` embeds `message.getContent()` and
nothing else — the sender is never embedded, never retrieved, never reaches the
prompt. Multiple accounts exist so a seeded channel reads as a conversation
between people in screenshots instead of one account talking to itself. Pass
`--single-user` to post everything as the calling account; the sender is never
embedded either way, but the leading `Name: ` prefix stays in the text, so
results are equivalent rather than bit-identical.

When posting as a speaker, the `Name: ` prefix is stripped from the message —
the sender field now carries it. Transcripts keep their inline speaker labels,
because that is what a real Deepgram transcript looks like.

## Running

```bash
# 0. Labeller check — no backend, no login, no Groq key needed.
python3 ai/eval/run_eval.py selftest

export RECALL_USER=<your username> RECALL_PASS=<your password>

# 1. Seed. --tag suffixes channel names so reseeding does not collide.
python3 ai/eval/run_eval.py seed --tag 3

# 2. One run per arm, restarting the app between each.
#    Set RECALL_RETRIEVAL_MODE in .env, rebuild, then:
python3 ai/eval/run_eval.py ask --out ai/eval/results-baseline.json
python3 ai/eval/run_eval.py ask --out ai/eval/results-demote.json
python3 ai/eval/run_eval.py ask --out ai/eval/results-filter.json

# 3. Compare.
python3 ai/eval/run_eval.py report ai/eval/results-*.json
```

Each result row stores the mode the **server** reported, so a forgotten restart
shows up as duplicate modes instead of a fabricated difference.

## Rate limiting — read this before trusting a run

Groq's free tier allows 30 requests/minute. One seeded message costs up to four
calls (one decision-extraction plus up to three supersession checks).

`DecisionService` catches every LLM exception and returns `false`. **A
rate-limited message is therefore filed as "not a decision", indistinguishable
from a genuine one.** The first seeding attempt here hit the limit within 60
seconds and produced 0/5 correct supersessions with no error surfaced to the
caller — the corpus looked fine and was worthless.

`RagService` has the matching problem on the query side: when answer generation
fails it returns the raw retrieved context verbatim, which looks like an answer
in a results file.

Guards now in place:

- `--rpm` (default 24) paces every LLM-triggering request in both `seed` and `ask`
- seeding posts one message at a time and waits for it to be fully ingested,
  so supersession checks cannot interleave
- seeding reports per scenario whether t1 was actually marked superseded, and
  says to check the log for `rate_limit_exceeded` before believing a miss
- `ask` flags any row where the answer equals the joined context as
  `llm_fallback`, and `report` warns if any exist

If a seed reports MISS, check first:

```bash
docker compose -f docker-compose.local.yml logs app --since 10m | grep -c rate_limit_exceeded
```

Non-zero means the corpus is corrupt — reseed at a lower `--rpm`. Zero means
the extractor genuinely failed, which is a finding worth reporting.

## Reading the output

`stale_chunk_retrieved` is the trustworthy number: an exact string match asking
whether the superseded chunk reached the prompt. No model judgement involved,
so it stands on its own as the retrieval-layer result.

`auto_label` is keyword triage for sorting rows, not ground truth — it cannot
judge stance, and "no, we left MongoDB" contains the stale keyword. Every row
keeps its raw `answer` and an empty `human_label`. Hand-label before quoting any
answer-quality percentage.

## Cleaning up seeded channels

Seeded channels accumulate one set per `--tag`. To remove a set (replace the
tag suffix):

```sql
BEGIN;
DELETE FROM memory_vectors      WHERE channel_id IN (SELECT id FROM channels WHERE name LIKE 'eval-%-3');
DELETE FROM call_transcripts    WHERE channel_id IN (SELECT id FROM channels WHERE name LIKE 'eval-%-3');
DELETE FROM channel_messages    WHERE channel_id IN (SELECT id FROM channels WHERE name LIKE 'eval-%-3');
DELETE FROM channel_memberships WHERE channel_id IN (SELECT id FROM channels WHERE name LIKE 'eval-%-3');
DELETE FROM channels            WHERE name LIKE 'eval-%-3';
COMMIT;
```

```bash
docker compose -f docker-compose.local.yml exec postgres psql -U postgres -d recall
```

Speaker accounts are left alone — they are reused by the next run.

## Files

| File | Role |
|---|---|
| `corpus.json` | 5 scenarios × 6 query paraphrases, 8 padding decisions each, 4 controls |
| `run_eval.py` | harness: `seed` / `ask` / `report` / `selftest`, standard library only |
| `seed-state.json` | written by `seed`: channel ids, speaker accounts, supersession ground truth |
| `results-*.json` | written by `ask`, one per arm |
| `staleness-dataset.md` | the dataset and method, formatted for the paper |
