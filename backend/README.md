# OffensiveRed Backend

A thin FastAPI service that lets the RedSecAI JavaFX GUI drive the upstream
[**Decepticon**](https://github.com/PurpleAILAB/Decepticon) autonomous red-team
framework. **The backend implements no scanning logic of its own** — it shells
out to Decepticon's supported CLI and surfaces the results.

```
JavaFX GUI ──HTTP──▶ FastAPI (:8000) ──▶ backend/runner.py
                                            └─▶ python -m decepticon.cli scan ...
                                                  └─▶ Decepticon LangGraph runtime + LLM
                                            ◀── SARIF findings ──┘
```

## Layout

| File | Purpose |
| --- | --- |
| `api/main.py` | FastAPI app + endpoints the GUI calls |
| `api/models.py` | Request/response models (camelCase to match the Jackson client) |
| `runner.py` | The only glue: launches `decepticon-cli scan`, streams its JSONL log, parses the SARIF output into findings |

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

`pip install decepticon` is enough to wire everything up, but Decepticon routes
the actual operation to its own runtime. To get findings you must also have:

- the **Decepticon LangGraph runtime** reachable at `DECEPTICON_API_URL`
  (default `http://localhost:2024`), and
- an **LLM provider / proxy** configured with valid credentials.

See the [Decepticon docs](https://github.com/PurpleAILAB/Decepticon) for
standing those up. **Until they are running, a scan fails fast with a clear
"runtime not reachable" message and performs no action** — the wrapper never
attacks anything on its own.

## Safety

Decepticon is a real, dual-use offensive security tool. Only point it at systems
you own or are explicitly authorized to test. When the GUI's **Safe Mode** box is
checked, the backend passes a non-destructive rules-of-engagement instruction to
Decepticon; it does not, by itself, make an autonomous agent harmless. Use
responsibly and legally.
