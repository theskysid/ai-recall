# Dummy users

Throwaway local accounts for seeding and demoing the evaluation corpus.
**Local development only.** These credentials are committed in plain text and
every account shares one weak password — never create them against a deployed
instance, and never reuse these passwords anywhere real.

## Speaker accounts

Created automatically by `run_eval.py seed` via `POST /auth/signup` (no email
OTP on that route), and reused on every later run. Password is the
`SPEAKER_PASSWORD` constant in `run_eval.py`.

| Username | Password | Email | Speaks in |
|---|---|---|---|
| `priya` | `EvalSpeaker123` | priya@eval.local | S1 database, S4 auth |
| `dev` | `EvalSpeaker123` | dev@eval.local | S1 database, S4 auth, controls |
| `marco` | `EvalSpeaker123` | marco@eval.local | S1 database, S4 auth, controls |
| `sam` | `EvalSpeaker123` | sam@eval.local | S2 deploy date, S5 scope |
| `ana` | `EvalSpeaker123` | ana@eval.local | S2 deploy date, S5 scope, controls |
| `ravi` | `EvalSpeaker123` | ravi@eval.local | S2 deploy date, S5 scope |
| `lena` | `EvalSpeaker123` | lena@eval.local | S3 budget |
| `tom` | `EvalSpeaker123` | tom@eval.local | S3 budget |

Usernames are the speaker name lowercased — the backend normalises usernames to
lowercase, so `Priya` and `priya` are the same account.

## Harness account

The account that creates the channels, posts the transcripts, and runs the
queries. This is a **real account you already own**, not one the harness
creates — pass it in rather than hardcoding it:

```bash
export RECALL_USER=<your username>
export RECALL_PASS=<your password>
```

It owns every seeded channel, so it is the one to log in as when taking
screenshots of the channel memory or the Ask Recall widget.

## Creating them without seeding

The harness creates accounts on demand, so normally you never do this by hand.
If you want them in place first:

```bash
for u in priya dev marco sam ana ravi lena tom; do
  curl -s -X POST http://localhost:8080/auth/signup \
    -H 'Content-Type: application/json' \
    -d "{\"username\":\"$u\",\"password\":\"EvalSpeaker123\",\"email\":\"$u@eval.local\"}"
  echo
done
```

Re-running is harmless: a duplicate username returns `Username is already in
use` and the harness falls through to logging in.

## Removing them

Accounts are left in place between runs on purpose. To clear them out, remove
their channel memberships and messages first — `users` is referenced by both:

```sql
BEGIN;
DELETE FROM channel_memberships WHERE user_id IN (SELECT id FROM users WHERE email LIKE '%@eval.local');
DELETE FROM channel_messages    WHERE sender_id IN (SELECT id FROM users WHERE email LIKE '%@eval.local');
DELETE FROM users               WHERE email LIKE '%@eval.local';
COMMIT;
```

Check the foreign keys before running this — anything else referencing `users`
needs deleting first, or the transaction rolls back.

## Why these exist

Only for how a seeded channel *looks*. `MemoryIngestionService.ingestMessage`
embeds `message.getContent()` and nothing else, so the sender never reaches an
embedding, a retrieval result, or the prompt. Seeding with `--single-user`
produces the same measurements — it just renders every message as coming from
one person, which is misleading in a screenshot of a team conversation.

See [README.md](README.md) for the full runbook.
