export const meta = {
  name: 'md-accuracy-audit',
  description: 'Verify every project markdown file against the actual code, then fix what drifted',
  phases: [
    { title: 'Verify', detail: 'one agent per doc group, checking every factual claim against source' },
    { title: 'Fix', detail: 'each group applies its own corrections' },
    { title: 'Critic', detail: 'cross-document contradictions and missed coverage' },
  ],
}

const ROOT = '/home/theskysid/Desktop/coding/Projects/ai_recall'

const GROUPS = [
  {
    key: 'guide',
    files: ['CLAUDE.md', 'ai/project.md', 'ai/architecture.md', 'ai/conventions.md'],
    focus: 'These are the docs an agent reads before changing code, so a wrong path or stale feature list actively misleads. Check directory names, module layout, build commands, ports, listed features, and every relative link target.',
  },
  {
    key: 'readme',
    files: ['README.md'],
    focus: 'Setup instructions, prerequisites, env vars, commands, ports, URLs. Every command must actually work from a clean checkout.',
  },
  {
    key: 'product',
    files: ['PRODUCT.md'],
    focus: 'Feature claims. Does each described capability actually exist in the code? Flag removed features still described and shipped features missing.',
  },
  {
    key: 'api',
    files: ['backend/API_DOCS.md'],
    focus: 'Every endpoint: HTTP method, exact path, request body fields, response shape, auth requirement. Compare against every @RestController and @Controller in the backend. Flag documented-but-absent and present-but-undocumented endpoints.',
  },
  {
    key: 'rag-auth',
    files: ['backend/RAG_API_DOCS.md', 'backend/AUTH_README.md'],
    focus: 'RAG endpoints and response fields against AiController/RagService/RagContextDTO; auth flows against AuthController/AuthenticationService/SecurityConfig/JwtAuthenticationFilter. Note the retrieval mode config recently added.',
  },
  {
    key: 'misc-eval',
    files: ['backend/HELP.md', 'frontend/README.md', 'ai/eval/README.md', 'ai/eval/staleness-dataset.md'],
    focus: 'HELP.md is likely Spring Initializr boilerplate - say so if it is stale or useless. frontend/README.md likewise for Vite. The two ai/eval docs are new: verify their claims about EvalController endpoints, run_eval.py flags, the three retrieval modes, repository method names, and the speaker accounts against the actual files.',
  },
]

const FINDINGS = {
  type: 'object',
  properties: {
    group: { type: 'string' },
    findings: {
      type: 'array',
      items: {
        type: 'object',
        properties: {
          file: { type: 'string' },
          severity: { type: 'string', enum: ['wrong', 'stale', 'missing', 'nit'] },
          location: { type: 'string', description: 'heading or line reference' },
          claim: { type: 'string', description: 'what the doc says' },
          reality: { type: 'string', description: 'what the code actually does, with file:line evidence' },
          fix: { type: 'string', description: 'the corrected text or what to change' },
        },
        required: ['file', 'severity', 'location', 'claim', 'reality', 'fix'],
      },
    },
    verified_ok: { type: 'array', items: { type: 'string' }, description: 'notable claims checked and found correct' },
  },
  required: ['group', 'findings'],
}

const APPLIED = {
  type: 'object',
  properties: {
    group: { type: 'string' },
    applied: { type: 'array', items: { type: 'string' } },
    skipped: { type: 'array', items: { type: 'string' } },
  },
  required: ['group', 'applied'],
}

phase('Verify')

const results = await pipeline(
  GROUPS,
  (g) => agent(
    `You are auditing documentation accuracy in the repo at ${ROOT}. Working directory is ${ROOT}.

This is "Recall" (codebase name: Echo Messaging): a Spring Boot (Java 21) backend in backend/ and a React+Vite frontend in frontend/, PostgreSQL 16 + pgvector, STOMP/SockJS WebSockets, LiveKit calls, Deepgram transcription, local MiniLM embeddings, Groq for LLM calls.

Audit these files: ${g.files.join(', ')}

Focus: ${g.focus}

Method - this matters:
- READ each doc fully, then verify EVERY factual claim against the actual source. Do not assume a claim is right because it sounds plausible.
- Use Grep/Glob/Read on the real files. Check controllers, entities, config files, docker-compose*.yml, .env.example, package.json, pom.xml.
- Check every relative link resolves to a file that exists.
- Check every shell command references a path/script that exists.
- Recent context: the retrieval layer just gained a recall.retrieval.mode setting (filter/demote/baseline) and an EvalController behind recall.eval.enabled. Docs written before that may be stale.
- Do NOT edit anything in this phase. Report only.
- If a whole file is generated boilerplate with no project-specific value, say so plainly rather than nitpicking it.

Report findings with file:line evidence for every "reality" claim. An unverified suspicion is not a finding - either prove it from source or drop it.`,
    { label: `verify:${g.key}`, phase: 'Verify', schema: FINDINGS }
  ),
  (res, g) => {
    if (!res || !res.findings || res.findings.length === 0) {
      log(`${g.key}: no corrections needed`)
      return { group: g.key, applied: [], skipped: [], findings: [] }
    }
    return agent(
      `Working directory ${ROOT}. Apply these documentation corrections to ${g.files.join(', ')}.

Findings to apply:
${JSON.stringify(res.findings, null, 2)}

Rules:
- Only edit the files listed above. Touch nothing else, and no source code.
- Before applying each fix, re-verify it against the source yourself. If a finding is wrong, skip it and say why - do not apply a bad correction.
- Match the surrounding document's tone, formatting and heading style. Do not restructure or rewrite sections that are already correct.
- Keep the diff minimal: correct what is wrong, add what is genuinely missing, delete what describes something that no longer exists.
- Do not add changelogs, "last updated" stamps, or notes about this audit.

Return what you applied and what you skipped with reasons.`,
      { label: `fix:${g.key}`, phase: 'Fix', schema: APPLIED }
    ).then(a => ({ ...a, findings: res.findings }))
  }
)

phase('Critic')

const summary = results.filter(Boolean).map(r => ({
  group: r.group,
  applied: r.applied,
  skipped: r.skipped,
  findings: (r.findings || []).map(f => `${f.file} [${f.severity}] ${f.claim} -> ${f.reality}`),
}))

const critic = await agent(
  `Working directory ${ROOT}. A documentation audit just corrected these project markdown files:
CLAUDE.md, README.md, PRODUCT.md, ai/project.md, ai/architecture.md, ai/conventions.md,
backend/API_DOCS.md, backend/RAG_API_DOCS.md, backend/AUTH_README.md, backend/HELP.md,
frontend/README.md, ai/eval/README.md, ai/eval/staleness-dataset.md

What each group did:
${JSON.stringify(summary, null, 2)}

Your job is to find what the audit MISSED. Specifically:
1. Cross-document contradictions - two docs stating different things about the same fact (ports, paths, commands, env vars, feature availability, endpoint shapes).
2. Facts that are now wrong in a doc NOT owned by the group that changed a related fact.
3. Claims no group verified at all.
4. Any fix that was applied incorrectly - re-read the changed regions and check them against source.

Run 'git diff --stat' and 'git diff' on the markdown files to see exactly what changed. Verify the changes are correct.

Do NOT edit anything. Report concrete, source-backed problems only, ordered by severity. If the docs are now consistent, say so plainly rather than inventing work.`,
  { label: 'critic:cross-doc', phase: 'Critic' }
)

return { groups: summary, critic }
