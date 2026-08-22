import os
import requests
import re

api_key = os.environ["GROQ_API_KEY"]
url = "https://api.groq.com/openai/v1/chat/completions"

with open("backend/src/main/java/com/theskysid/echobackend/memory/service/DecisionService.java", "r") as f:
    content = f.read()

# Extract CONFLICT_SYSTEM
match = re.search(r'private static final String CONFLICT_SYSTEM = """(.*?)""";', content, re.DOTALL)
if match:
    system_prompt = match.group(1).strip()
else:
    print("Could not find CONFLICT_SYSTEM")
    exit(1)

cases = [
    {
        "name": "1. unrelated padding decision vs the target decision",
        "old": "We decided to use MongoDB for the database.",
        "new": "We've decided to use Loki for observability.",
        "expected": "NONE"
    },
    {
        "name": "2. C2 independent decision with similar wording",
        "old": "We decided to use PostgreSQL for the database.",
        "new": "We've decided to switch the frontend from React to Vue.",
        "expected": "NONE"
    },
    {
        "name": "3. C1 restatement",
        "old": "We decided to use PostgreSQL.",
        "new": "The team has decided to use PostgreSQL.",
        "expected": "NONE"
    },
    {
        "name": "4. true Mongo -> t2 reversal",
        "old": "We decided to use MongoDB.",
        "new": "We've decided to switch to PostgreSQL.",
        "expected": "SUPERSEDE"
    }
]

print("Running 4-case probe at temperature=0.0...\n")

for c in cases:
    user_prompt = f'Old decision: "{c["old"]}"\nNew statement: "{c["new"]}"\nAnswer with one token: SUPERSEDE, UNRESOLVED or NONE.'
    payload = {
        "model": "llama-3.1-8b-instant",
        "temperature": 0.0,
        "messages": [
            {"role": "system", "content": system_prompt},
            {"role": "user", "content": user_prompt}
        ]
    }
    
    headers = {
        "Authorization": f"Bearer {api_key}",
        "Content-Type": "application/json"
    }
    
    resp = requests.post(url, json=payload, headers=headers)
    if resp.status_code == 200:
        actual = resp.json()["choices"][0]["message"]["content"].strip()
        print(f"[{c['name']}]")
        print(f"  Old: {c['old']}")
        print(f"  New: {c['new']}")
        print(f"  Expected: {c['expected']}")
        print(f"  Actual:   {actual}")
        print(f"  Result:   {'PASS' if actual.upper() == c['expected'] else 'FAIL'}\n")
    else:
        print(f"Error {resp.status_code}: {resp.text}")

