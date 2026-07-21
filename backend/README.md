# OffensiveRed Backend

A thin FastAPI service that lets the RedSecAI JavaFX GUI drive the upstream
[**Decepticon**](https://github.com/PurpleAILAB/Decepticon) autonomous red-team
framework. **The backend implements no scanning logic of its own** — it builds
Decepticon's agents with the published factory API (the documented
[library-usage](https://github.com/PurpleAILAB/Decepticon/blob/main/docs/library-usage.md)
path) and runs the engagement **in-process**, then surfaces the results.

```
JavaFX GUI ──HTTP──▶ FastAPI (:8000) ──▶ backend/runner.py
                                            └─▶ create_decepticon_agent()  (in-process)
                                                  └─▶ LangGraph orchestrator + specialist sub-agents
                                                        └─▶ LLM proxy + bash sandbox (+ optional KG store)
                                            ◀── SARIF findings (export_findings_to_sarif) ──┘
```

## Layout

| File | Purpose |
| --- | --- |
| `api/main.py` | FastAPI app + endpoints the GUI calls |
| `api/models.py` | Request/response models (camelCase to match the Jackson client) |
| `runner.py` | The only glue: builds a Decepticon agent via its factory, streams the in-process run into a log, and exports the engagement's findings to SARIF |

## Endpoints

| Method | Path | Description |
| --- | --- | --- |
| `GET` | `/health` | Liveness check |
| `POST` | `/scan/start` | Start a scan (`{target, scope, safeMode}`) → `{scanId, ...}` |
| `GET` | `/scan/status/{id}` | Live status + recent log lines |
| `GET` | `/scan/result/{id}` | Findings + report for the GUI tabs |
| `GET` | `/scans` | All scans this process has run |
| `GET` | `/workflow/status` | Current engine phase |

## Running

```bash
pip install -r requirements.txt        # installs the decepticon package
python -m backend.api.main             # serves http://127.0.0.1:8000
```

Smoke-test it:

```bash
curl http://127.0.0.1:8000/health
```

## Prerequisites for a real scan

`pip install decepticon` is enough to wire everything up, and the orchestrator
graph runs **in this process** (no separate LangGraph platform server needed).
To actually execute an engagement you must still have Decepticon's backing
services configured:

- an **LLM provider / proxy** resolved by `LLMFactory` (default
  `http://localhost:4000`) with valid credentials, and
- the **bash sandbox** the specialist sub-agents execute in.

Reported findings additionally require Decepticon's **KnowledgeGraph store**
(Neo4j via `DECEPTICON_NEO4J_URI` / `DECEPTICON_NEO4J_USER` /
`DECEPTICON_NEO4J_PASSWORD`); without it a run still executes but reports zero
findings, exactly like the upstream `scan` CLI.

See the [Decepticon docs](https://github.com/PurpleAILAB/Decepticon) for
standing those up. **Until the LLM/sandbox services are running, a scan fails
fast with a clear message and performs no action** — the wrapper never attacks
anything on its own.

### Choosing which agent runs

By default the backend drives the full `decepticon` orchestrator (all
specialist sub-agents). On constrained setups you can run a single specialist
instead by setting the `OFFENSIVERED_AGENT` environment variable to a role name
before starting the server, e.g.:

```bash
OFFENSIVERED_AGENT=recon python -m backend.api.main
```

## Safety

Decepticon is a real, dual-use offensive security tool. Only point it at systems
you own or are explicitly authorized to test. When the GUI's **Safe Mode** box is
checked, the backend passes a non-destructive rules-of-engagement instruction to
Decepticon; it does not, by itself, make an autonomous agent harmless. Use
responsibly and legally.
