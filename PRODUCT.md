# Product

<!-- impeccable:product-schema 1 -->

## Platform

web

## Users

Primary users are **small social groups — friends who already talk daily** — using
Recall as their everyday chat: channels for the group, DMs for one-to-one, video
calls when typing isn't enough. They are not evaluating a tool; they are in the
middle of a conversation.

The **pitch audience is small product teams** — a handful of people in a few named
channels who go away, come back, and need to know what was settled. Product
positioning and marketing surfaces speak to this audience; the day-to-day usage
scene is the social one. Design for the social scene, argue for the team case.

There are no roles beyond channel membership: a channel has an owner (transferred
to the longest-standing member when the owner leaves) and members. No admin tier,
no permissions model.

The product is deployed to be **actually used**, not only demonstrated. Onboarding,
empty states, error states, and real mobile behavior are load-bearing, not polish.

## Product Purpose

Recall is team chat that keeps the record. Everything said in a channel — typed
messages and spoken words in calls — is retained, indexed, and questionable in
plain language afterward. Success is a user who was absent asking the channel what
happened and getting a grounded answer with its sources, instead of scrolling.

The name of the problem it solves: conversations dissolve. Chat apps store
messages but do not remember; calls vanish entirely; decisions get re-litigated
because nobody can point at where they were made.

## Positioning

The mechanism a neighboring chat app could not truthfully copy without building
it: **the channel itself is the memory.** Three parts, all real in the code —

1. **Calls transcribe into the same record as the text.** A video call's speech
   becomes a channel-scoped transcript that is embedded alongside messages, so
   asking the channel a question searches what was *said* as well as typed.
2. **Decisions are extracted and dated, and supersession is explicit.** When a
   channel settles something it is flagged as a decision; a later decision that
   replaces it marks the older one `superseded` rather than deleting it, and
   retrieval excludes superseded decisions from the context —
   `MemoryVectorRepository.findTop5ActiveOnly` is the only retrieval query, and
   there is no mode switch. The record keeps its own history of changing its
   mind.
3. **Retrieval is channel-scoped and cited.** Answers come back with the message
   and transcript IDs they were drawn from.

This is a *record*, not an "AI summary." Do not soften it into generic assistant
language.

## Operating Context

- Users live inside a channel feed for long stretches; the Ask-AI widget sits
  above that feed rather than in a separate destination.
- Video calls start from within a channel and return to it.
- Real usage is mixed desktop and phone; the app ships distinct desktop and
  mobile layouts (`DesktopLayout` / `MobileLayout` + drawer), so mobile is a
  first-class scene, not a squeeze of the desktop one.
- Sign-in paths: password, email OTP, and Google OAuth. (Phone/SMS OTP was
  removed and must not return.)
- Self-hosted: Docker Compose on a single EC2 box. The frontend container is
  Caddy: it terminates TLS (Let's Encrypt) and proxies `/api`, `/auth` and
  `/ws` to the backend, so everything is one origin and there is one web
  server, not two. Embedding runs in-process; there is no external embedding
  vendor.

## Capabilities and Constraints

Confirmed and working:

- Auth (password, email OTP, Google OAuth2), JWT in an httpOnly cookie.
- Friendship and real-time 1:1 DMs with a per-conversation retention window —
  six hours, one day, or seven days (default one day) — plus online presence.
  DM content is never embedded into memory.
- Channels: create with auto-generated invite code, join by code, leave, list.
  Real-time messaging over STOMP with persisted history.
- LiveKit video calls per channel, backend-minted scoped tokens.
- Deepgram transcription of call audio, stored per channel.
- Vector memory: messages and transcripts embedded locally (all-MiniLM-L6-v2,
  384-dim) into pgvector; `GET /api/channels/{id}/ask?q=` retrieves up to 5
  channel-scoped, non-superseded memories and synthesizes a grounded answer with
  source IDs.
- Decision extraction and supersession.
- Decision timeline endpoint: `GET /api/channels/{id}/decisions` (active +
  superseded, newest first).
- Channel memory panel: decision timeline (active/superseded) and per-call
  transcripts, collapsed above the feed.
- Profile management: edit profile, and link/unlink email and Google sign-in
  methods after signup (`/api/profile/**`).
- Installable as a PWA — fullscreen from the home screen, launching into
  `/chatarea`.

Constraints future work must respect:

- **Additive only.** Auth, friend chat/DMs, and channels (text, calls, memory)
  are stable. Extend alongside them; do not remove or replace.
- **Removed and not to be reintroduced:** global/public chat, phone/SMS OTP.
- Design tokens are defined and commented in `src/index.css`: a `--nb-*`
  notebook base (sheet/rule/ink/bind/seal/stamp) with semantic aliases over it
  (`--bg`/`--text`/`--accent`/`--record`/`--alarm`); per-surface CSS files under
  `src/styles/` consume them. Tailwind and shadcn were removed; the app is
  hand-written CSS throughout, including `src/components/ui/`. There is no
  component library.
- Terminology, as used in product surfaces: *channel*, *invite code*, *memory*,
  *decision*, *superseded*, *transcript*, *direct message*. Not: workspace,
  server, thread, assistant, bot. UI copy holds to this, but the RAG answer
  prompt still opens "You are an AI project assistant" (`RagService`), so
  generated answers can adopt assistant persona language — the prompt should be
  reworded to a non-persona instruction.
- Retrieval staleness was measured once against three retrieval strategies;
  excluding superseded decisions won and is now the only behaviour. The harness
  has been removed — the record is in [ai/eval/](ai/eval/). Never a product
  surface; do not design UI for it.

Undecided (do not invent):

- Pricing, plans, or any commercial model. None exists.

## Brand Commitments

- **Name:** Recall. (`echo-frontend` / "Echo Messaging" is the codebase's legacy
  name and is not user-facing, except `public/favicon.svg`, which still reads
  "Echo" — in the browser tab and as the PWA icon — and must be corrected.)
- **Tagline in use:** "Chat with a memory."
- **Voice:** plain, declarative, unhyped. Short sentences. States what the product
  does as fact, not benefit-speak. Existing copy is the reference — e.g. "Calls
  that write themselves down," "Nothing worth saying should have to be said
  twice." No exclamation marks, no "supercharge," no assistant persona.
- No logo asset exists; the wordmark is set in type.

## Evidence on Hand

Real: the running application itself and every capability listed above — these
are demonstrable, not claims.

**Absent, and must not be fabricated:** testimonials, named customers or logos,
user or team counts, uptime or latency benchmarks, press, funding, pricing,
security certifications, and "trusted by" proof of any kind. The example
question-and-answer card on the landing page is illustrative and is labeled as
such; any future example content must stay visibly illustrative rather than
posing as a real customer's data.

## Product Principles

1. **The record is the product.** Every feature should make the channel's memory
   more complete or more answerable. Features that add conversation without
   adding record are off-thesis.
2. **Show the source.** An answer without its citations is a guess. Anything the
   memory produces points back at the messages and transcripts behind it.
3. **Nothing is quietly erased.** Superseded, expired, and left-behind states are
   shown as what they are. Retention windows are stated exactly.
4. **The conversation, not the tool.** The interface recedes during ordinary
   chatting; the memory surfaces when asked, not as an ambient assistant.
5. **Claim only what runs.** Copy describes shipped behavior. No aspirational
   features, no invented proof.

## Accessibility & Inclusion

No product-specific standard has been established by the user. Baseline craft
applies (keyboard reachability, focus visibility, contrast, respecting
`prefers-reduced-motion`); treat as a floor, not a documented requirement.

---

<!-- Interview note: the primary-user and destination answers above are the user's
own. A third question — which product facts are binding — was not answered; the
"Evidence on Hand" absences and the self-hosted-embeddings, DM-expiry, and
supersession facts are taken as binding because the code and shipped copy already
assert them, not because the user confirmed them. Revisit if that is wrong. -->
