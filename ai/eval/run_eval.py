#!/usr/bin/env python3
"""
Staleness-blindness evaluation harness for RECALL.

Three commands:

  seed    load corpus.json into fresh channels through the real ingestion path,
          then record which decisions the extractor actually marked superseded
  ask     run every query against /api/eval/.../ask and save the raw results
  report  compare saved result files arm by arm

The retrieval arm is server-side config, so it cannot be switched from here.
Run once per arm, restarting the backend in between:

  RECALL_EVAL_ENABLED=true RECALL_RETRIEVAL_MODE=baseline  docker compose ... up -d app
  python3 run_eval.py ask --out results-baseline.json
  RECALL_EVAL_ENABLED=true RECALL_RETRIEVAL_MODE=demote    docker compose ... up -d app
  python3 run_eval.py ask --out results-demote.json
  RECALL_EVAL_ENABLED=true RECALL_RETRIEVAL_MODE=filter    docker compose ... up -d app
  python3 run_eval.py ask --out results-filter.json
  python3 run_eval.py report results-*.json

Each result row records the mode the server reported, not the mode requested,
so a forgotten restart shows up as duplicate modes instead of a fake result.

Standard library only. No pip install.
"""

import argparse
import json
import os
import sys
import time
import urllib.error
import urllib.parse
import urllib.request

HERE = os.path.dirname(os.path.abspath(__file__))
CORPUS = os.path.join(HERE, "corpus.json")
STATE = os.path.join(HERE, "seed-state.json")

# Ingestion is @Async and every decision costs 1-4 Groq round trips, so a
# channel can take a while to settle. Poll rather than sleeping a guess.
POLL_INTERVAL = 2.0
POLL_TIMEOUT = 300.0

# Groq's free tier allows 30 requests/minute, and DecisionService swallows a 429
# by returning false — a rate-limited message is silently filed as "not a
# decision" and supersession never fires. That corrupts the corpus without
# raising anything, so pace every LLM-triggering call instead of discovering it
# afterwards in the MISS warning.
SEED_CALLS_PER_POST = 4   # 1 extract + up to 3 supersession checks
ASK_CALLS_PER_QUERY = 1   # generateAnswer


class Pacer:
    """Spaces requests so LLM calls per minute stay under `rpm`."""

    def __init__(self, rpm):
        self.min_gap = 60.0 / rpm if rpm > 0 else 0.0
        self.next_free = 0.0

    def wait(self, calls):
        if self.min_gap <= 0:
            return
        now = time.monotonic()
        if now < self.next_free:
            time.sleep(self.next_free - now)
        self.next_free = max(now, self.next_free) + self.min_gap * calls


# Speaker accounts. Sender identity is never embedded — MemoryIngestionService
# stores message.getContent() and nothing else — so these change no result. They
# exist so a channel reads as a conversation between people in screenshots
# instead of one user talking to themselves.
SPEAKER_PASSWORD = "EvalSpeaker123"
SPEAKER_EMAIL = "{name}@eval.local"


class Api:
    def __init__(self, base, username, password):
        self.base = base.rstrip("/")
        self.cookie = None
        self._login(username, password)

    @classmethod
    def ensure(cls, base, username, password, email):
        """Log in, creating the account first if it does not exist yet."""
        try:
            return cls(base, username, password)
        except SystemExit:
            req = urllib.request.Request(
                base.rstrip("/") + "/auth/signup",
                data=json.dumps({"username": username, "password": password, "email": email}).encode(),
                method="POST")
            req.add_header("Content-Type", "application/json")
            try:
                urllib.request.urlopen(req, timeout=30).read()
            except urllib.error.HTTPError as e:
                raise SystemExit(f"Could not create speaker '{username}': {e.read().decode()[:200]}")
            return cls(base, username, password)

    def _request(self, method, path, body=None, params=None):
        url = self.base + path
        if params:
            url += "?" + urllib.parse.urlencode(params)
        data = json.dumps(body).encode() if body is not None else None
        req = urllib.request.Request(url, data=data, method=method)
        req.add_header("Content-Type", "application/json")
        if self.cookie:
            req.add_header("Cookie", self.cookie)
        try:
            with urllib.request.urlopen(req, timeout=180) as resp:
                raw = resp.read().decode()
                set_cookie = resp.headers.get("Set-Cookie")
                if set_cookie:
                    self.cookie = set_cookie.split(";")[0]
                return json.loads(raw) if raw else {}
        except urllib.error.HTTPError as e:
            detail = e.read().decode()[:400]
            raise SystemExit(f"{method} {path} failed: HTTP {e.code} {detail}")
        except urllib.error.URLError as e:
            raise SystemExit(f"{method} {path} failed: {e.reason} (is the backend up at {self.base}?)")

    def _login(self, username, password):
        self._request("POST", "/auth/login", {"username": username, "password": password})
        if not self.cookie:
            raise SystemExit("Login returned no JWT cookie.")

    def create_channel(self, name):
        return self._request("POST", "/api/channels", {"name": name, "description": "staleness eval"})

    def join_channel(self, invite_code):
        return self._request("POST", "/api/channels/join", {"inviteCode": invite_code})

    def post_message(self, channel_id, content):
        return self._request("POST", f"/api/eval/channels/{channel_id}/message", {"content": content})

    def post_transcript(self, channel_id, content):
        return self._request("POST", f"/api/eval/channels/{channel_id}/transcript", {"content": content})

    def memory_count(self, channel_id):
        return self._request("GET", f"/api/eval/channels/{channel_id}/memory-count")["count"]

    def decisions(self, channel_id):
        return self._request("GET", f"/api/channels/{channel_id}/decisions")

    def ask(self, channel_id, query):
        return self._request("GET", f"/api/eval/channels/{channel_id}/ask", params={"q": query})


def split_speaker(line):
    """'Priya: we ship Friday' -> ('Priya', 'we ship Friday'). None if unprefixed."""
    name, sep, rest = line.partition(": ")
    if sep and name.isalpha() and len(name) <= 20:
        return name, rest
    return None, line


def wait_for_count(api, channel_id, expected, label):
    """Block until the async pipeline has stored `expected` vectors."""
    deadline = time.time() + POLL_TIMEOUT
    while time.time() < deadline:
        count = api.memory_count(channel_id)
        if count >= expected:
            return count
        time.sleep(POLL_INTERVAL)
    raise SystemExit(
        f"{label}: only {api.memory_count(channel_id)}/{expected} vectors after "
        f"{POLL_TIMEOUT:.0f}s. Ingestion is stuck or the Groq key is rate-limited - "
        f"check the backend log before trusting any results."
    )


def cmd_seed(args, corpus):
    api = Api(args.base, args.username, args.password)
    pacer = Pacer(args.rpm)
    state = {"base": args.base, "rpm": args.rpm, "multi_user": args.multi_user,
             "speakers": [], "scenarios": {}, "controls": {}}

    cast = {}

    def session(name):
        """Session for a named speaker, creating the account on first use."""
        if name not in cast:
            handle = name.lower()
            cast[name] = Api.ensure(args.base, handle, SPEAKER_PASSWORD,
                                    SPEAKER_EMAIL.format(name=handle))
            state["speakers"].append({"speaker": name, "username": handle,
                                      "email": SPEAKER_EMAIL.format(name=handle)})
            print(f"  speaker: {handle}")
        return cast[name]

    def admit(lines, invite_code):
        """Join every speaker appearing in these lines to the channel."""
        if not args.multi_user:
            return
        for name in sorted({s for s in (split_speaker(l)[0] for l in lines) if s}):
            session(name).join_channel(invite_code)

    def post(cid, line):
        """Post as the line's speaker, dropping the now-redundant name prefix."""
        name, text = split_speaker(line)
        if args.multi_user and name:
            session(name).post_message(cid, text)
        else:
            api.post_message(cid, line)

    total_posts = sum(len(s["t1_messages"]) + len(s["padding"]) + 1 for s in corpus["scenarios"])
    total_posts += sum(len(c["messages"]) for c in corpus["controls"]["cases"])
    est = total_posts * SEED_CALLS_PER_POST / args.rpm
    print(f"{total_posts} posts at {args.rpm} LLM calls/min - roughly {est:.0f} min.\n")

    for sc in corpus["scenarios"]:
        name = f"{sc['channel']}-{args.tag}"
        channel = api.create_channel(name)
        cid = channel["id"]
        print(f"[{sc['id']}] channel {cid} ({name})")
        lines = sc["t1_messages"] + sc["padding"]
        admit(lines, channel["inviteCode"])

        # Order matters: original decision, then unrelated padding, then the
        # reversal. Padding after t1 keeps the channel above the LIMIT 5 floor
        # without sitting between the two decisions being linked.
        #
        # One message at a time, each fully ingested before the next: ingestion
        # is @Async, so posting a batch lets supersession checks interleave and
        # race over which decisions are active.
        posted = 0
        for msg in lines:
            pacer.wait(SEED_CALLS_PER_POST)
            post(cid, msg)
            posted += 1
            wait_for_count(api, cid, posted, f"{sc['id']} msg {posted}")

        pacer.wait(SEED_CALLS_PER_POST)
        api.post_transcript(cid, sc["t2_transcript"])
        posted += 1
        wait_for_count(api, cid, posted, f"{sc['id']} t2")

        decisions = api.decisions(cid)
        stale = [d for d in decisions if d["superseded"]]
        marker = sc["stale_chunk_marker"]
        t1_marked = any(marker in d["content"] and d["superseded"] for d in decisions)

        state["scenarios"][sc["id"]] = {
            "channel_id": cid,
            "channel_name": name,
            "vectors": posted,
            "decisions_extracted": len(decisions),
            "decisions_superseded": len(stale),
            "t1_correctly_superseded": t1_marked,
            "superseded_content": [d["content"] for d in stale],
        }
        flag = "ok" if t1_marked else "MISS"
        print(f"  {flag}: {len(decisions)} decisions, {len(stale)} superseded, t1 marked={t1_marked}")

    ctl = corpus["controls"]
    name = f"{ctl['channel']}-{args.tag}"
    channel = api.create_channel(name)
    cid = channel["id"]
    admit([m for c in ctl["cases"] for m in c["messages"]], channel["inviteCode"])
    posted = 0
    for case in ctl["cases"]:
        for msg in case["messages"]:
            pacer.wait(SEED_CALLS_PER_POST)
            post(cid, msg)
            posted += 1
            wait_for_count(api, cid, posted, f"controls msg {posted}")
    decisions = api.decisions(cid)
    state["controls"] = {
        "channel_id": cid,
        "channel_name": name,
        "vectors": posted,
        "decisions": [{"content": d["content"], "superseded": d["superseded"]} for d in decisions],
    }
    print(f"[controls] channel {cid}: {len(decisions)} decisions, "
          f"{sum(1 for d in decisions if d['superseded'])} superseded")

    with open(STATE, "w") as f:
        json.dump(state, f, indent=2)
    print(f"\nWrote {STATE}")

    misses = [s for s, v in state["scenarios"].items() if not v["t1_correctly_superseded"]]
    if misses:
        print(
            f"\nWARNING: the extractor did not mark t1 superseded in {', '.join(misses)}.\n"
            "For those scenarios every arm sees the same data, so they measure the\n"
            "decision extractor, not retrieval - the retrieval comparison is void.\n"
            "Check the backend log for 'rate_limit_exceeded' first: DecisionService\n"
            "returns false on any LLM error, so a throttled call looks exactly like\n"
            "a genuine 'not a decision'. If the log is clean, the misses are real."
        )


def label(answer, sc, form):
    """
    First-pass automatic label. Keyword matching cannot judge an answer's stance,
    so treat this as triage: every row keeps its raw answer for human labelling.
    """
    low = answer.lower()
    has_current = any(k in low for k in sc["current_keywords"])
    has_stale = any(k in low for k in sc["stale_keywords"])
    if has_current and not has_stale:
        return "correct"
    if has_stale and not has_current:
        return "stale"
    if has_current and has_stale:
        # "Are we still on Mongo?" / "did it change?" invite naming both, so a
        # mention of the old value is only a blend for the neutral forms.
        return "correct_contrastive" if form in ("stale-anchored", "comparative") else "blended"
    return "unknown"


def cmd_ask(args, corpus):
    if not os.path.exists(STATE):
        raise SystemExit(f"No {STATE}. Run `seed` first.")
    with open(STATE) as f:
        state = json.load(f)

    api = Api(args.base, args.username, args.password)
    pacer = Pacer(args.rpm)
    rows = []

    for sc in corpus["scenarios"]:
        info = state["scenarios"][sc["id"]]
        cid = info["channel_id"]
        for q in sc["queries"]:
            for seed in range(args.repeat):
                pacer.wait(ASK_CALLS_PER_QUERY)
                res = api.ask(cid, q["text"])
                retrieved = res.get("retrieved") or []
                rows.append({
                    "scenario": sc["id"],
                    "change_type": sc["change_type"],
                    "query_id": q["id"],
                    "form": q["form"],
                    "query": q["text"],
                    "seed": seed,
                    "mode": res.get("mode"),
                    "answer": res.get("answer", ""),
                    # Objective, LLM-independent: did the dead decision reach the prompt?
                    "stale_chunk_retrieved": any(
                        sc["stale_chunk_marker"] in c for c in retrieved),
                    "chunks_retrieved": len(retrieved),
                    "auto_label": label(res.get("answer", ""), sc, q["form"]),
                    "t1_correctly_superseded": info["t1_correctly_superseded"],
                    # RagService returns the raw context verbatim when the LLM
                    # call fails, so a throttled run yields a row that looks
                    # like an answer but never went through the model.
                    "llm_fallback": res.get("answer", "") == "\n\n".join(retrieved),
                    "human_label": None,
                })
                print(f"{sc['id']} {q['id']} s{seed} [{res.get('mode')}] "
                      f"{rows[-1]['auto_label']}"
                      f"{' STALE-CHUNK' if rows[-1]['stale_chunk_retrieved'] else ''}"
                      f"{' LLM-FALLBACK' if rows[-1]['llm_fallback'] else ''}")

    ctl_cid = state["controls"]["channel_id"]
    for case in corpus["controls"]["cases"]:
        for seed in range(args.repeat):
            pacer.wait(ASK_CALLS_PER_QUERY)
            res = api.ask(ctl_cid, case["query"])
            low = res.get("answer", "").lower()
            rows.append({
                "scenario": "CONTROL",
                "change_type": case["kind"],
                "query_id": case["id"],
                "form": "control",
                "query": case["query"],
                "seed": seed,
                "mode": res.get("mode"),
                "answer": res.get("answer", ""),
                "stale_chunk_retrieved": False,
                "chunks_retrieved": len(res.get("retrieved") or []),
                "auto_label": "correct" if any(k in low for k in case["expect_keywords"]) else "incorrect",
                "t1_correctly_superseded": None,
                "llm_fallback": res.get("answer", "") == "\n\n".join(res.get("retrieved") or []),
                "human_label": None,
            })
            print(f"{case['id']} s{seed} [{res.get('mode')}] {rows[-1]['auto_label']}")

    modes = {r["mode"] for r in rows}
    with open(args.out, "w") as f:
        json.dump({"modes": sorted(modes), "rows": rows}, f, indent=2)
    print(f"\nWrote {args.out} ({len(rows)} rows, server mode(s): {sorted(modes)})")


def cmd_report(args):
    arms = []
    for path in args.files:
        with open(path) as f:
            data = json.load(f)
        arms.append((path, data))

    modes = [tuple(d["modes"]) for _, d in arms]
    if len(set(modes)) != len(modes):
        print("WARNING: two result files report the same server mode. The backend\n"
              "was probably not restarted between arms - those results are duplicates.\n")

    print(f"{'arm':<12} {'n':>4} {'correct':>8} {'stale':>7} {'blend':>7} "
          f"{'unknown':>8} {'stale-chunk retrieved':>22}")
    print("-" * 74)
    for path, data in arms:
        rows = [r for r in data["rows"] if r["scenario"] != "CONTROL"]
        n = len(rows) or 1
        mode = ",".join(data["modes"])

        def pct(pred):
            return 100.0 * sum(1 for r in rows if pred(r)) / n

        correct = pct(lambda r: r["auto_label"] in ("correct", "correct_contrastive"))
        stale = pct(lambda r: r["auto_label"] == "stale")
        blend = pct(lambda r: r["auto_label"] == "blended")
        unknown = pct(lambda r: r["auto_label"] == "unknown")
        chunk = pct(lambda r: r["stale_chunk_retrieved"])
        print(f"{mode:<12} {len(rows):>4} {correct:>7.1f}% {stale:>6.1f}% {blend:>6.1f}% "
              f"{unknown:>7.1f}% {chunk:>21.1f}%")

    fallbacks = [r for _, d in arms for r in d["rows"] if r.get("llm_fallback")]
    if fallbacks:
        print(f"\nWARNING: {len(fallbacks)} rows returned raw context instead of a generated\n"
              "answer - the LLM call failed (usually rate limiting). Those rows are not\n"
              "answers and must not be scored. Lower --rpm and re-run them.")

    print("\nControls (a drop here means the filter costs you correct answers):")
    for path, data in arms:
        ctl = [r for r in data["rows"] if r["scenario"] == "CONTROL"]
        ok = sum(1 for r in ctl if r["auto_label"] == "correct")
        print(f"  {','.join(data['modes']):<12} {ok}/{len(ctl)} passed")

    skipped = [r for _, d in arms for r in d["rows"] if r["t1_correctly_superseded"] is False]
    if skipped:
        scenarios = sorted({r["scenario"] for r in skipped})
        print(f"\nNOTE: {', '.join(scenarios)} had no supersession recorded at seed time.\n"
              "Those rows measure the decision extractor, not retrieval. Exclude them\n"
              "from the retrieval claim or report them as a separate finding.")

    print("\nauto_label is keyword triage, not ground truth. Hand-label the answers\n"
          "in the result files before quoting any of these percentages.")


def cmd_selftest(corpus):
    """The labeller is the only branching logic here, so it gets the one check."""
    s1 = next(s for s in corpus["scenarios"] if s["id"] == "S1")

    assert label("You are using PostgreSQL.", s1, "direct") == "correct"
    assert label("The team decided on MongoDB with an Atlas cluster.", s1, "direct") == "stale"
    assert label("They chose MongoDB, and also discussed PostgreSQL.", s1, "direct") == "blended"
    # Naming the dead option is the right answer to a question that names it.
    assert label("No - you moved off MongoDB to PostgreSQL.", s1, "stale-anchored") == "correct_contrastive"
    assert label("Yes, it changed from MongoDB to PostgreSQL.", s1, "comparative") == "correct_contrastive"
    assert label("I do not have any information about that.", s1, "direct") == "unknown"

    s3 = next(s for s in corpus["scenarios"] if s["id"] == "S3")
    # S3's trap: $30k is the live total AND the stale search allocation, so an
    # answer naming both figures must not score as correct.
    assert label("The budget is $50,000 - $30,000 search and $20,000 social.", s3, "direct") == "blended"
    assert label("The Q3 budget is $30,000, all to search.", s3, "direct") == "correct"

    print("selftest ok")


def main():
    p = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    p.add_argument("--base", default=os.environ.get("RECALL_BASE", "http://localhost:8080"))
    p.add_argument("--username", default=os.environ.get("RECALL_USER"))
    p.add_argument("--password", default=os.environ.get("RECALL_PASS"))
    sub = p.add_subparsers(dest="cmd", required=True)

    # Groq free tier is 30 rpm; 24 leaves room for langchain4j's internal retries.
    rpm = dict(type=int, default=24, help="LLM calls/min ceiling (Groq free tier is 30)")

    s = sub.add_parser("seed", help="load the corpus into fresh channels")
    s.add_argument("--tag", default="1", help="suffix for channel names, so reseeding does not collide")
    s.add_argument("--rpm", **rpm)
    s.add_argument("--single-user", dest="multi_user", action="store_false",
                   help="post everything as the calling account instead of one account per speaker "
                        "(identical results - sender is never embedded - but worse screenshots)")

    a = sub.add_parser("ask", help="run every query against the current retrieval arm")
    a.add_argument("--out", required=True)
    a.add_argument("--repeat", type=int, default=3, help="runs per query (LLM output varies)")
    a.add_argument("--rpm", **rpm)

    r = sub.add_parser("report", help="compare saved result files")
    r.add_argument("files", nargs="+")

    sub.add_parser("selftest", help="check the answer labeller against known cases")

    args = p.parse_args()

    if args.cmd == "report":
        return cmd_report(args)

    with open(CORPUS) as f:
        corpus = json.load(f)

    if args.cmd == "selftest":
        return cmd_selftest(corpus)

    if not args.username or not args.password:
        raise SystemExit("Set RECALL_USER and RECALL_PASS (or pass --username/--password).")

    if args.cmd == "seed":
        cmd_seed(args, corpus)
    else:
        cmd_ask(args, corpus)


if __name__ == "__main__":
    main()
