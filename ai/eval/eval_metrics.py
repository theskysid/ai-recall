import json
import collections

files = ['results-baseline.json', 'results-demote.json', 'results-filter.json']

data = []
for f in files:
    with open(f, 'r') as fp:
        j = json.load(fp)
        data.extend(j['rows'])

modes = ['baseline', 'demote', 'filter']

metrics = {m: {'total': 0, 'stale_retrieved': 0, 'labels': collections.Counter(), 'llm_fallback': 0} for m in modes}

for row in data:
    m = row['mode']
    if m not in metrics: continue
    metrics[m]['total'] += 1
    if row.get('stale_chunk_retrieved'):
        metrics[m]['stale_retrieved'] += 1
    metrics[m]['labels'][row.get('auto_label')] += 1
    if row.get('llm_fallback'):
        metrics[m]['llm_fallback'] += 1

print("## Performance Metrics\n")
print("| Mode | Total Queries | Stale Chunk Retrieved | Stale Retrieved % | Correct Label | Stale Label | LLM Fallbacks |")
print("|------|---------------|-----------------------|-------------------|---------------|-------------|---------------|")
for m in modes:
    t = metrics[m]['total']
    sr = metrics[m]['stale_retrieved']
    srp = f"{sr/t*100:.1f}" if t > 0 else "0.0"
    cl = metrics[m]['labels'].get('correct', 0)
    sl = metrics[m]['labels'].get('stale', 0)
    lf = metrics[m]['llm_fallback']
    print(f"| {m} | {t} | {sr} | {srp}% | {cl} | {sl} | {lf} |")

print("\n--- MERMAID 1 (Stale Retrieved) ---")
for m in modes:
    t = metrics[m]['total']
    sr = metrics[m]['stale_retrieved']
    srp = float(f"{sr/t*100:.1f}") if t > 0 else 0.0
    print(f"{m} : {srp}")

print("\n--- MERMAID 2 (Labels) ---")
for m in modes:
    cl = metrics[m]['labels'].get('correct', 0)
    sl = metrics[m]['labels'].get('stale', 0)
    print(f"{m} : Correct={cl}, Stale={sl}")
