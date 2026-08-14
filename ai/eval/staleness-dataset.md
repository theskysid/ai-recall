# RECALL Staleness-Blindness Evaluation Dataset

Synthetic corpus for measuring whether supersession-aware retrieval suppresses
outdated decisions. 5 decision-evolution scenarios, each with a reinforced
original decision (t1, chat) and a terse later reversal (t2, call transcript).

**The two output columns in each scenario table are hypotheses, not results.**
They state the predicted failure mode so the experiment is pre-registered. The
measured outcome is in [Results](#results) — read that, not the predictions,
when citing anything.

## Design notes

- t1 carries **3–4 reinforcing messages**, t2 is a **single terse reversal**.
  This is deliberate: it creates the "heavily discussed old fact" condition
  where lexical/semantic mass favours the stale answer.
- t2 is a transcript excerpt, so both `SourceType` paths are exercised.
- The Day labels are narrative only: the scenarios read as Day 0 → Day 5–9, but
  `corpus.json` carries no timestamps and seeding posts t1, padding and t2 in
  one pass.
- Scenarios span five change types: entity swap, date shift, budget cut,
  vendor change, scope reduction.

---

## Scenario 1 — Database choice (entity swap)

| Field | Content |
|---|---|
| **Channel** | `eval-s1-database` |
| **t1 — original context** (chat, Day 0) | **Priya:** Alright, decision made — we're going with **MongoDB** for the primary datastore.<br>**Dev:** Good call, the flexible schema will save us a ton of migration pain early on.<br>**Priya:** Exactly. I'll set up the Atlas cluster this week.<br>**Marco:** +1 on Mongo. I've used it on two projects, aggregation pipeline is solid.<br>**Dev:** I'll start the Mongoose models tomorrow then. |
| **t2 — superseding context** (call transcript, Day 7) | **[Architecture Review — 00:14:22]**<br>**Priya:** …so after the load test we're reversing the datastore decision. We're moving to **PostgreSQL**, not MongoDB. The relational joins we need for reporting were killing us in Mongo.<br>**Marco:** Agreed, Postgres it is. |
| **User query** | "What database are we using?" |
| **Gold answer** | PostgreSQL. The team initially chose MongoDB but reversed that decision on Day 7 after load testing showed relational joins for reporting performed poorly. |
| **Predicted baseline failure** | "The team decided to use MongoDB for the primary datastore, with an Atlas cluster and Mongoose models. There was also discussion of PostgreSQL." — leads with the stale answer or blends both without resolving. |
| **Predicted RECALL output** | "You're using PostgreSQL. The team moved off MongoDB after load testing." |
| **Change type** | Entity swap |

---

## Scenario 2 — Deployment date (date shift)

| Field | Content |
|---|---|
| **Channel** | `eval-s2-deploy-date` |
| **t1 — original context** (chat, Day 0) | **Sam:** Confirmed with leadership — **production deploy is November 14th**.<br>**Ana:** Noted, I'll freeze the release branch on the 12th.<br>**Sam:** Perfect. Nov 14 it is, that gives QA a full week.<br>**Ravi:** I'll book the war room for the 14th. |
| **t2 — superseding context** (call transcript, Day 9) | **[Release Sync — 00:03:51]**<br>**Sam:** Small change — the deploy is **pushed to December 5th**. The compliance sign-off won't land before the 14th, so November is off the table.<br>**Ana:** December 5th, updating the calendar now. |
| **User query** | "When are we deploying to production?" |
| **Gold answer** | December 5th. It was originally set for November 14th but moved because compliance sign-off wouldn't complete in time. |
| **Predicted baseline failure** | "Production deploy is scheduled for November 14th, with the release branch freezing on the 12th." — the Nov 14 date appears in four separate chunks and dominates retrieval. |
| **Predicted RECALL output** | "The deploy is December 5th — it slipped from November 14th because of compliance sign-off." |
| **Change type** | Date shift |

---

## Scenario 3 — Marketing budget (numeric reduction)

| Field | Content |
|---|---|
| **Channel** | `eval-s3-budget` |
| **t1 — original context** (chat, Day 0) | **Lena:** Q3 paid budget is locked: **$50,000**, split 60/40 between search and social.<br>**Tom:** $50k works. I'll brief the agency on the 60/40 split.<br>**Lena:** Great — 30k search, 20k social.<br>**Tom:** Agency confirmed they can absorb the $50k spend across the quarter. |
| **t2 — superseding context** (call transcript, Day 5) | **[Budget Review — 00:21:07]**<br>**Lena:** Finance clawed back part of it. The Q3 paid budget is now **$30,000**, not fifty. All of the cut comes out of social — search stays at 30k, social goes to zero.<br>**Tom:** So 30k, search only. Got it. |
| **User query** | "How much is the Q3 marketing budget?" |
| **Gold answer** | $30,000, all allocated to search. It was originally $50,000 split 60/40 between search and social, but finance cut it and the reduction came entirely out of social. |
| **Predicted baseline failure** | "The Q3 paid budget is $50,000, split 60/40 — $30,000 search and $20,000 social." — the `$30,000` figure appears in *both* contexts with different meanings (search allocation vs. total), a strong blending trap. |
| **Predicted RECALL output** | "The Q3 budget is $30,000, all going to search. It was cut from $50,000." |
| **Change type** | Budget cut (with numeric ambiguity trap) |

---

## Scenario 4 — Auth provider (vendor change)

| Field | Content |
|---|---|
| **Channel** | `eval-s4-auth` |
| **t1 — original context** (chat, Day 0) | **Dev:** We're going with **Auth0** for authentication. Decision is final.<br>**Priya:** Makes sense, SSO and MFA out of the box.<br>**Dev:** Right, and the free tier covers us to 7,000 MAU.<br>**Marco:** I'll wire up the Auth0 React SDK on the frontend. |
| **t2 — superseding context** (call transcript, Day 6) | **[Security Review — 00:11:40]**<br>**Dev:** Auth0 is out. Legal won't approve the data-residency terms, so we're **self-hosting Keycloak** instead.<br>**Priya:** Keycloak, confirmed. I'll kill the Auth0 tenant. |
| **User query** | "Which auth provider did we pick?" |
| **Gold answer** | Self-hosted Keycloak. Auth0 was chosen first but rejected on Day 6 because legal would not approve its data-residency terms. |
| **Predicted baseline failure** | "The team selected Auth0, using the React SDK, with the free tier covering up to 7,000 MAU." |
| **Predicted RECALL output** | "Keycloak, self-hosted. Auth0 was dropped over data-residency terms." |
| **Change type** | Vendor change |

---

## Scenario 5 — Release scope (scope reduction)

| Field | Content |
|---|---|
| **Channel** | `eval-s5-scope` |
| **t1 — original context** (chat, Day 0) | **Ravi:** v2 scope is agreed: **offline mode, real-time collaboration, and the analytics dashboard**. All three ship together.<br>**Ana:** All three, confirmed. I'll write the epics.<br>**Ravi:** Offline mode is the headline feature, that's the one marketing wants.<br>**Sam:** Real-time collab is the biggest lift but the team's up for it. |
| **t2 — superseding context** (call transcript, Day 8) | **[Scope Cut Call — 00:07:15]**<br>**Ravi:** We're cutting v2 down. **Only the analytics dashboard ships in v2** — offline mode and real-time collaboration both move to v3.<br>**Ana:** Analytics only for v2. Updating the roadmap. |
| **User query** | "What's shipping in v2?" |
| **Gold answer** | Only the analytics dashboard. Offline mode and real-time collaboration were originally in v2 but were deferred to v3 on Day 8. |
| **Predicted baseline failure** | "v2 includes offline mode, real-time collaboration, and the analytics dashboard, with offline mode as the headline feature." — a partial-overlap trap: the stale answer is a *superset* of the correct one, so surface-level overlap scoring rates it as partially right. |
| **Predicted RECALL output** | "Just the analytics dashboard. Offline mode and real-time collaboration moved to v3." |
| **Change type** | Scope reduction (superset trap) |

---

## Query variants

Five scenarios is `n=5` — too small for any claim. Each scenario runs against
six paraphrases (30 queries × 3 repeats = 90 scenario trials per arm, plus 12
control trials) to separate retrieval behaviour from prompt luck:

| # | Form | Scenario 1 example |
|---|---|---|
| Q1 | Direct | "What database are we using?" |
| Q2 | Past-tense | "What database did we decide on?" |
| Q3 | Current-state | "What's the current datastore decision?" |
| Q4 | Stale-anchored | "Are we still on MongoDB?" |
| Q5 | Indirect | "What should I use for the new service's persistence layer?" |
| Q6 | Comparative | "Did the database choice change?" |

Q4 is the sharpest probe — it lexically anchors on the dead decision, so
retrieval must actively override the query's own bias.

## Negative controls

Without these you cannot claim supersession is *safe*, only that it is
*active*. One control channel (`eval-controls`) carries four cases — 7 seeded
messages — covering the four rows below:

| Control | Purpose |
|---|---|
| Never-superseded decision | Penalty must not degrade normal retrieval |
| Two independent decisions, similar wording | `replaces()` must return NO — measures false-supersession rate |
| Reinstated decision (A → B → back to A) | Chained supersession; the middle decision must end up dead |
| Query with no matching decision | Must answer "I don't know", not confabulate from nearest neighbour |

## Metrics

| Metric | Definition |
|---|---|
| **Stale-answer rate** | % of trials whose answer asserts the t1 value as current. Primary metric. |
| **Blend rate** | % asserting both values without resolving which is current |
| **Correct rate** | % asserting the t2 value as current |
| **Stale-chunk retrieval rate** | % of trials where the t1 vector entered the top-5. *Retrieval-layer metric — independent of the LLM.* |
| **Supersession recall** | % of the 5 scenarios where `supersedes_id` was actually set on t1 |
| **Supersession precision** | 1 − false-supersession rate on the negative controls |

Report the last two separately. If supersession recall is below 100%, every
downstream number is measuring the extractor, not the retriever — and that
distinction is the paper.

## The three arms

All three share the same embedder, prompt, model and top-k. They differ in one
clause of one query, so nothing else can explain a gap between them. Selected
by `recall.retrieval.mode` (env `RECALL_RETRIEVAL_MODE`):

| Arm | Mode | Retrieval clause | Repository method |
|---|---|---|---|
| **A — baseline RAG** | `baseline` | `ORDER BY embedding <=> :q` | `findTop5Baseline` |
| **B — demote** | `demote` | `… + (CASE WHEN status = 'SUPERSEDED' THEN 10 ELSE 0 END)` | `findTop5ByChannelAndSimilarity` |
| **C — filter** (default) | `filter` | `WHERE … AND status <> 'SUPERSEDED'` | `findTop5ActiveOnly` |

Arms B and C keyed off `supersedes_id` when the run below was made; they were
later switched to the equivalent `status` column so that an UNRESOLVED item —
contested but not replaced — is neither demoted nor excluded. For a corpus
with no UNRESOLVED items the two conditions select identically, so the
measurements stand.

Arm B was the shipped behaviour when this study was designed. It demotes rather
than excludes, so with fewer than 5 active vectors in a channel it backfills the
remaining slots with dead decisions — which is why arm C exists and is now the
default.

## Results

Corpus tag 3, channels 15–20, 8 speaker accounts. 5 scenarios × 6 query
paraphrases × 3 repeats = 90 scenario trials per arm, plus 12 control trials.
Supersession recall **5/5**; supersession precision **1.0** (no false
supersession on C2, correct A→B→A chain on C3). Zero `llm_fallback` rows and
zero `rate_limit_exceeded` entries in the app log across all three arms, so no
result is a silently throttled LLM call. Each arm's rows carry the mode the
*server* reported — `baseline`, `demote`, `filter`, all distinct — confirming
the restarts took.

### Retrieval layer

Exact string match on the superseded chunk. No model judgement enters this
number; it is the result that stands on its own.

| Arm | S1 | S2 | S3 | S4 | S5 | Stale-chunk retrieval rate |
|---|---|---|---|---|---|---|
| **A — baseline** | 18/18 | 18/18 | 18/18 | 18/18 | 18/18 | **100.0%** (90/90) |
| **B — demote** | 0/18 | 0/18 | 0/18 | 0/18 | 0/18 | **0.0%** (0/90) |
| **C — filter** | 0/18 | 0/18 | 0/18 | 0/18 | 0/18 | **0.0%** (0/90) |

Complete separation in every scenario, no exceptions.

### Answer layer

`auto_label` is keyword triage, not ground truth — **these percentages are not
citable until the rows are hand-labelled.** They are reported here for shape,
not for the paper.

| Arm | n | Correct | Stale | Blend | Unknown | Controls |
|---|---|---|---|---|---|---|
| A — baseline | 90 | 68.9% | 10.0% | 18.9% | 2.2% | 9/12 |
| B — demote | 90 | 71.1% | 3.3% | 10.0% | 15.6% | 12/12 |
| C — filter | 90 | 67.8% | 4.4% | 10.0% | 17.8% | 12/12 |

The elevated `unknown` in arms B and C is largely an artefact of the labeller:
with no stale keyword left in the answer there is less for it to match on. This
is exactly why the answer layer needs hand-labelling and the retrieval layer
does not.

Controls: the 3 baseline failures are all the unanswerable query ("What time is
standup?"), where leaked stale context prompted an answer instead of an
"I don't know". Both suppressed arms scored 12/12 — **suppression cost zero
correct answers.**

### What this does and does not show

1. **Suppressing superseded chunks works, completely.** Baseline places the
   reversed decision in the prompt on *every single query*; both suppression
   arms place it in *none*.

2. **Arms B and C are indistinguishable on this corpus.** The +10 distance
   penalty was already sufficient to push every superseded chunk out of the
   top-5, so the hard filter shows no measurable advantage here. The likely
   cause is the mitigation for the `LIMIT 5` confound: 8 padding decisions per
   channel also removed the sparse condition under which demotion was predicted
   to backfill dead decisions. Separating them requires channels with fewer
   than 5 live decisions. **The supported claim is "suppression works", not
   "hard filtering beats demotion."**

3. **Baseline still answered correctly most of the time.** 62 of 90 baseline
   trials were labelled correct *while the superseded chunk was in the prompt* —
   the model often reconciles the contradiction unaided. The defensible framing
   is therefore: **standard RAG feeds stale context on 100% of queries; the LLM
   rescues it only sometimes.** Not: "standard RAG returns outdated decisions."

## Running the experiment

Files: `corpus.json` (the data below, machine-readable) and `run_eval.py`
(harness, standard library only).

**Setup** — in `.env`:

```
RECALL_EVAL_ENABLED=true
RECALL_RETRIEVAL_MODE=filter
```

`RECALL_EVAL_ENABLED` exposes `/api/eval/**`, which is how chat and transcripts
get seeded over HTTP at all — chat normally arrives over STOMP and transcripts
only ever come back from Deepgram. Keep it `false` in production.

**1. Seed.** Content is posted through `ChannelService.postMessage` and
`MemoryIngestionService.ingestTranscript` — the real embedder, extractor and
supersession check. Nothing writes `supersedes_id` directly; doing so would
assume away the behaviour under test.

```bash
export RECALL_USER=you RECALL_PASS=...
python3 ai/eval/run_eval.py seed
```

Seeding records, per scenario, whether the extractor actually marked t1
superseded, and warns loudly where it did not. **Read that warning before
running anything else** — a scenario with no supersession recorded gives every
arm identical data, so it measures the decision extractor rather than retrieval.

**2. Run each arm.** The mode is server config, so restart between arms. Each
result row stores the mode the *server* reported, so a forgotten restart shows
up as duplicate modes rather than a fabricated difference.

```bash
# set RECALL_RETRIEVAL_MODE=baseline in .env, restart the app
python3 ai/eval/run_eval.py ask --out results-baseline.json
# then demote, then filter
python3 ai/eval/run_eval.py report results-*.json
```

Default is 3 runs per query (`--repeat`), because Groq/Llama output varies.

**3. Grade.** `run_eval.py` computes one metric that needs no judgement —
`stale_chunk_retrieved`, an exact string match against the retrieved chunks.
That is the retrieval-layer result and it stands on its own. The `auto_label`
field is keyword triage only; every row keeps its raw answer and an empty
`human_label` for hand-labelling before any percentage is quoted.

### Known confounds

| Confound | Effect | Mitigation |
|---|---|---|
| `LIMIT 5` in a sparse channel | With <5 active vectors, arm B returns the stale chunk anyway and arms A and B become indistinguishable | Each channel is padded with 8 unrelated decisions (in `corpus.json`). **This mitigation overshot** — it also removed the condition under which arm B fails, collapsing B and C to identical results (see [Results](#results)) |
| No temporal signal in the prompt | Context is joined with `\n\n`, no timestamps (`RagService`) — the LLM cannot prefer the newer fact even when both are retrieved | Report as a finding; a 4th arm with timestamped context would isolate it |
| `findTopDecisionsByChannel` LIMIT 3 | Supersession only checks the 3 nearest active decisions, so padding a channel can itself *cause* a supersession miss | Seeding reports supersession recall per scenario; vary padding size to test sensitivity |
| LLM-based `replaces()` | A non-deterministic 1-token YES/NO gate sits upstream of everything | Treat supersession recall as a separate reported metric, not a precondition |
| Keyword auto-labelling | Cannot judge stance — "no, we left MongoDB" contains the stale keyword | Contrastive query forms are labelled separately; hand-label before publishing |
