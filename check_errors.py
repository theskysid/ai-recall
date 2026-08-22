import urllib.request
import urllib.error
import urllib.parse
import json

base = "http://localhost:8080"
username = "eval_checker"
password = "password"

# signup
req = urllib.request.Request(
    base + "/auth/signup",
    data=json.dumps({"username": username, "password": password, "email": "eval_checker@example.com"}).encode(),
    method="POST"
)
req.add_header("Content-Type", "application/json")
try:
    urllib.request.urlopen(req, timeout=30)
except urllib.error.HTTPError as e:
    pass # probably already exists

# login
req = urllib.request.Request(
    base + "/auth/login",
    data=json.dumps({"username": username, "password": password}).encode(),
    method="POST"
)
req.add_header("Content-Type", "application/json")
with urllib.request.urlopen(req, timeout=30) as resp:
    cookie = resp.headers.get("Set-Cookie").split(";")[0]

# get errors
req = urllib.request.Request(base + "/api/eval/llm-errors", method="GET")
req.add_header("Cookie", cookie)
with urllib.request.urlopen(req, timeout=30) as resp:
    print(resp.read().decode())
