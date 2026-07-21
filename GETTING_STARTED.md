# Getting Started — Launching & Using OffensiveRed

OffensiveRed has **three layers**. Know which one you're starting:

```
┌─────────────┐    ┌──────────────────────────────┐    ┌────────────────────────────┐
│ JavaFX GUI  │ ─▶ │ OffensiveRed backend         │ ─▶ │ Decepticon backing services│
│ (desktop)   │    │ FastAPI  :8000               │    │ LLM proxy :4000 + sandbox  │
│             │    │ backend/runner.py            │    │ (+ optional KG store)      │
│             │    │ builds & runs the decepticon │    │                            │
│             │    │ orchestrator *in-process*    │    │                            │
└─────────────┘    └──────────────────────────────┘    └────────────────────────────┘
   you build         this repo (verified)                upstream PurpleAILAB stack
```

The backend follows Decepticon's documented **library-usage** path: it builds the
agent with `create_decepticon_agent()` and runs the LangGraph orchestrator *inside the
backend process* — there is **no separate LangGraph platform server** to stand up.

There are two ways to run it:

- **Dev / wiring mode** — backend + GUI only. The pipeline works end-to-end, but a
  scan fails fast with a clear *backing-services-not-reachable* message. Nothing
  offensive happens. Use this to confirm the app itself works. **No Docker, no API
  keys needed.**
- **Full mode** — also stand up Decepticon's LLM proxy + sandbox so scans actually
  execute. **Needs Docker + an LLM API key.**

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
# {"status":"ok","engine":"decepticon","integration":"library","agent":"decepticon","version":"1.0.0"}
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

## 3. Decepticon backing services (only for real findings)

The orchestrator runs **in-process** inside the backend, so you do **not** need the
LangGraph platform server. You do still need Decepticon's LLM proxy and bash sandbox,
which the upstream PurpleAILAB stack provides via **Docker**:

```bash
# install the official Decepticon control CLI
curl -fsSL https://decepticon.red/install | bash

# configure an LLM provider + credentials (stored in ~/.decepticon/.env)
decepticon onboard          # pick Anthropic/OpenAI/Gemini/Ollama, paste your key

# start the containerized stack (LLM proxy :4000, sandbox, dashboard :3000, ...)
decepticon

# helpers
decepticon status
decepticon logs
decepticon stop
```

`LLMFactory` resolves models through the proxy at `http://localhost:4000` and the
specialist sub-agents run bash in the sandbox. **Reported findings** additionally
require Decepticon's KnowledgeGraph store (Neo4j via `DECEPTICON_NEO4J_URI` /
`DECEPTICON_NEO4J_USER` / `DECEPTICON_NEO4J_PASSWORD`); without it a run still
executes but returns zero findings.

Optional — pick which agent the backend drives (defaults to the full orchestrator):

```bash
# PowerShell:  $env:OFFENSIVERED_AGENT = "recon"
export OFFENSIVERED_AGENT="recon"   # run one specialist instead of the orchestrator
```

Once the services are up, scans started from the GUI (or via curl) return real results.

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
| Scan ends `failed`, "backing services not reachable" | Decepticon's LLM proxy / sandbox isn't up (section 3). Expected in dev mode. |
| Scan `completed` with 0 findings | Run executed but no KnowledgeGraph store configured (section 3), or nothing was found. |
| First scan takes a while at `building-agent` | Normal — the agent (and its sub-agents) is built once per process, then reused. |
| Logs show `No credentials detected` | No LLM key configured — run `decepticon onboard` or set `ANTHROPIC_API_KEY`. |
| GUI won't start, `mvn` not found | Install Maven (section 2). |
| GUI can't reach backend | Make sure section 1 is running on `127.0.0.1:8000`. |

## ⚠️ Authorization

Decepticon is a real, dual-use offensive tool. **Only point it at systems you own or
are explicitly authorized to test.** Safe Mode passes a non-destructive rules-of-
engagement instruction, but a fully-configured autonomous agent can still take real
action — use it legally and responsibly.
