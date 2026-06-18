# Getting Started — Launching & Using OffensiveRed

OffensiveRed has **three layers**. Know which one you're starting:

```
┌─────────────┐    ┌──────────────────────┐    ┌──────────────────────────────┐
│ JavaFX GUI  │ ─▶ │ OffensiveRed backend │ ─▶ │ Decepticon runtime           │
│ (desktop)   │    │ FastAPI  :8000       │    │ LangGraph :2024 + LLM proxy  │
│             │    │ backend/runner.py    │    │ :4000 + sandbox (Docker)     │
└─────────────┘    └──────────────────────┘    └──────────────────────────────┘
   you build         this repo (verified)         upstream PurpleAILAB stack
```

There are two ways to run it:

- **Dev / wiring mode** — backend + GUI only. The pipeline works end-to-end, but a
  scan fails fast with *"runtime not reachable"*. Nothing offensive happens. Use this
  to confirm the app itself works. **No Docker, no API keys needed.**
- **Full mode** — also stand up the Decepticon runtime so scans produce real findings.
  **Needs Docker + an LLM API key.**

---

## 1. Backend (always required)

```bash
# from the repo root
pip install -r backend/requirements.txt      # installs the decepticon package
python -m backend.api.main                    # serves http://127.0.0.1:8000
```

Smoke-test in another terminal:

```bash
curl http://127.0.0.1:8000/health
# {"status":"ok","engine":"decepticon","version":"1.0.0"}
```

Leave this running.

## 2. GUI (needs Maven)

Maven isn't bundled — install it once:

```bash
choco install maven        # or: scoop install maven   (or download from maven.apache.org)
```

Then run the JavaFX app (Java 17+ required; you have 25):

```bash
mvn -f frontend clean javafx:run
```

In the window: enter a **target**, choose a **scope**, keep **Safe Mode** checked,
click **Start Scan**, watch the **Logs** tab, then read the **Findings / Attack Paths /
Reports** tabs.

> Prefer no GUI? Skip this and drive the backend with `curl` (see section 4).

## 3. Decepticon runtime (only for real findings)

This is upstream PurpleAILAB infrastructure and runs in **Docker**.

```bash
# install the official Decepticon control CLI
curl -fsSL https://decepticon.red/install | bash

# configure an LLM provider + credentials (stored in ~/.decepticon/.env)
decepticon onboard          # pick Anthropic/OpenAI/Gemini/Ollama, paste your key

# start the containerized stack (LangGraph :2024, LLM proxy :4000, sandbox, dashboard :3000)
decepticon

# helpers
decepticon status
decepticon logs
decepticon stop
```

Our backend talks to `DECEPTICON_API_URL` (default `http://localhost:2024`). If your
stack uses a different URL, set it **before** launching the backend:

```bash
# PowerShell:  $env:DECEPTICON_API_URL = "http://localhost:2024"
export DECEPTICON_API_URL="http://localhost:2024"
```

Once the runtime is up, scans started from the GUI (or via curl) return real results.

## 4. Driving it via the API (no GUI)

```bash
# start a scan
curl -X POST http://127.0.0.1:8000/scan/start \
  -H "Content-Type: application/json" \
  -d '{"target":"https://a-target-you-are-authorized-to-test","scope":["quick"],"safeMode":true}'
# -> {"scanId":"<id>", ...}

curl http://127.0.0.1:8000/scan/status/<id>     # live status + recent logs
curl http://127.0.0.1:8000/scan/result/<id>     # findings + report
```

Scope maps to a Decepticon scan profile: `quick`→quick, `full`→deep, everything
else→standard.

## Troubleshooting

| Symptom | Cause / fix |
| --- | --- |
| Scan ends `failed`, "runtime not reachable" | Decepticon runtime isn't up (section 3). Expected in dev mode. |
| Logs show `No credentials detected` | No LLM key configured — run `decepticon onboard` or set `ANTHROPIC_API_KEY`. |
| GUI won't start, `mvn` not found | Install Maven (section 2). |
| GUI can't reach backend | Make sure section 1 is running on `127.0.0.1:8000`. |

## ⚠️ Authorization

Decepticon is a real, dual-use offensive tool. **Only point it at systems you own or
are explicitly authorized to test.** Safe Mode passes a non-destructive rules-of-
engagement instruction, but a fully-configured autonomous agent can still take real
action — use it legally and responsibly.
