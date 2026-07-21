"""In-process adapter that drives the real ``decepticon`` library.

This module is the *only* glue between the OffensiveRed backend and the
upstream `Decepticon <https://github.com/PurpleAILAB/Decepticon>`_ framework.
It follows Decepticon's documented **library-usage** path
(https://github.com/PurpleAILAB/Decepticon/blob/main/docs/library-usage.md):
the engagement is built with the published agent factory
(``decepticon.agents.standard.decepticon.create_decepticon_agent``) and run
*in-process* over the LangGraph runnable interface. There is no CLI subprocess
and no separate LangGraph platform server -- the compiled orchestrator graph
executes inside this process.

Findings are read back exactly the way Decepticon's own ``scan`` CLI does: the
engagement's ``graph.json`` KnowledgeGraph is exported to SARIF via
``decepticon.tools.research.sarif_export.export_findings_to_sarif`` and then
flattened into the finding list the JavaFX GUI consumes. No scanning or
reporting logic is reimplemented here.

A real run still needs Decepticon's backing services, which are the operator's
responsibility:

* an **LLM provider / proxy** resolved by ``LLMFactory`` (default
  ``http://localhost:4000``), and
* the **bash sandbox** used by the specialist sub-agents.

Findings additionally require the **KnowledgeGraph store** (Neo4j via
``DECEPTICON_NEO4J_*``); without it a run still executes but reports zero
findings, just like the upstream CLI. When the LLM/sandbox services are not
reachable the run fails fast with a clear message and never attacks anything
on its own.

By default the full ``decepticon`` orchestrator (all specialist sub-agents) is
driven. Set ``OFFENSIVERED_AGENT`` to a single role (e.g. ``recon``) to build
and run one specialist instead -- useful on constrained setups where standing
up the whole stack is not practical.
"""

from __future__ import annotations

import asyncio
import json
import os
import re
import threading
import time
import uuid
from dataclasses import dataclass, field
from pathlib import Path
from typing import Any, Optional

# OffensiveRed scan scope -> Decepticon scan-mode profile.
_SCOPE_TO_MODE = {
    "quick": "quick",
    "full": "deep",
    "web": "standard",
    "network": "standard",
    "api": "standard",
}

# Wall-clock caps per mode (seconds) so a run can't hang forever.
_MODE_TIMEOUT = {"quick": 600, "standard": 1800, "deep": 3600}

# SARIF result.level -> GUI severity label.
_LEVEL_SEVERITY = {
    "error": "High",
    "warning": "Medium",
    "note": "Low",
    "none": "Info",
}

# Which Decepticon agent factory to drive. "decepticon" is the full
# orchestrator; any specialist role (recon, exploit, ...) is also valid.
_AGENT_ROLE = os.environ.get("OFFENSIVERED_AGENT", "decepticon").strip() or "decepticon"

_SCHEME_RE = re.compile(r"^[a-z][a-z0-9+.\-]*://", re.IGNORECASE)


def _normalize_target(target: str) -> str:
    """Promote a bare host/domain to an ``https://`` URL for the brief.

    A value that is already a URL, a VCS ref, or an existing filesystem path is
    left untouched; anything else is treated as a web target.
    """
    t = target.strip()
    if _SCHEME_RE.match(t) or t.startswith(("git@", "git+", "ssh://")):
        return t
    if Path(t).exists():
        return t
    return f"https://{t}"


def _engagement_workspace(engagement: str) -> Path:
    """Directory Decepticon persists the engagement under.

    Mirrors the upstream ``scan`` CLI: honour
    ``DECEPTICON_ENGAGEMENT_WORKSPACE`` when set, otherwise fall back to
    ``~/.decepticon/workspace/<engagement>``.
    """
    override = os.environ.get("DECEPTICON_ENGAGEMENT_WORKSPACE")
    if override:
        return Path(override)
    return Path.home() / ".decepticon" / "workspace" / engagement


@dataclass
class ScanRecord:
    """In-memory state for one scan, polled by the GUI via /scan/result."""

    scan_id: str
    target: str
    scope: list[str]
    safe_mode: bool
    scan_mode: str
    engagement: str
    status: str = "pending"          # pending|running|completed|failed
    current_phase: str = "queued"
    progress: float = 0.0
    error: Optional[str] = None
    logs: list[str] = field(default_factory=list)
    results: dict = field(default_factory=dict)
    created_at: float = field(default_factory=time.time)
    started_at: Optional[float] = None
    completed_at: Optional[float] = None

    def log(self, message: str) -> None:
        stamp = time.strftime("%H:%M:%S", time.localtime())
        self.logs.append(f"[{stamp}] {message}")

    def summary(self) -> dict:
        return {
            "scan_id": self.scan_id,
            "target": self.target,
            "scope": self.scope,
            "scan_mode": self.scan_mode,
            "engagement": self.engagement,
            "safe_mode": self.safe_mode,
            "status": self.status,
            "current_phase": self.current_phase,
            "progress": round(self.progress, 3),
            "error": self.error,
            "created_at": self.created_at,
            "completed_at": self.completed_at,
            "log_count": len(self.logs),
        }

    def status_view(self) -> dict:
        view = self.summary()
        view["logs"] = self.logs[-100:]
        return view

    def result_view(self) -> dict:
        return {
            "scan_id": self.scan_id,
            "target": self.target,
            "status": self.status,
            "results": self.results,
        }


class DecepticonRunner:
    """Builds and drives a Decepticon agent in-process for each scan."""

    def __init__(self) -> None:
        self.scans: dict[str, ScanRecord] = {}
        self._tasks: dict[str, asyncio.Task] = {}
        self.role = _AGENT_ROLE
        # The compiled agent is expensive to build and is reused across scans.
        self._agent: Any = None
        self._agent_lock = threading.Lock()

    # -- lifecycle -------------------------------------------------------------
    async def start_scan(
        self, target: str, scope: list[str], safe_mode: bool = True
    ) -> str:
        scan_id = str(uuid.uuid4())
        scope = scope or ["full"]
        mode = _SCOPE_TO_MODE.get(scope[0].lower(), "standard")
        record = ScanRecord(
            scan_id=scan_id,
            target=target,
            scope=scope,
            safe_mode=safe_mode,
            scan_mode=mode,
            engagement=f"offensivered-{scan_id[:8]}",
        )
        record.log(
            f"Queued Decepticon scan of '{target}' "
            f"(agent={self.role}, scan-mode={mode}, safe_mode={safe_mode})."
        )
        self.scans[scan_id] = record
        # Building and running the graph is blocking/CPU-bound; keep it off the
        # event loop so /health and status polling stay responsive.
        self._tasks[scan_id] = asyncio.create_task(
            asyncio.to_thread(self._run_blocking, record)
        )
        return scan_id

    # -- agent construction ----------------------------------------------------
    def _get_agent(self) -> Any:
        """Build the Decepticon agent once and cache it (thread-safe)."""
        with self._agent_lock:
            if self._agent is not None:
                return self._agent
            # Imported lazily: importing decepticon is heavy, so a plain
            # ``import backend`` (e.g. for /health) must not pull it in.
            import decepticon.agents as agents_pkg  # noqa: PLC0415

            factory = getattr(agents_pkg, f"create_{self.role}_agent", None)
            if factory is None:
                raise ValueError(
                    f"Unknown Decepticon agent role {self.role!r}. Set "
                    "OFFENSIVERED_AGENT to 'decepticon' or a specialist role "
                    "such as 'recon'."
                )
            self._agent = factory()
            return self._agent

    def _build_invocation(
        self, record: ScanRecord, workspace: Path
    ) -> tuple[dict, dict]:
        """Construct the (state_input, config) pair for the LangGraph run.

        Mirrors the upstream ``scan`` CLI: the scope/RoE travel as a JSON block
        in the opening user message, and the engagement slug + workspace are
        injected through ``config.configurable`` (the launcher channel the
        ``EngagementContextMiddleware`` hydrates into state).
        """
        scope_payload = {
            "targets": [_normalize_target(record.target)],
            "scope_mode": "full",
            "scan_mode": record.scan_mode,
            "safe_mode": record.safe_mode,
            "instruction": self._instruction(record),
        }
        content = (
            "Run a one-shot authorized security engagement. Scope and rules of "
            "engagement are attached as JSON:\n\n"
            + json.dumps(scope_payload, indent=2)
        )
        state_input = {
            "messages": [{"role": "user", "content": content}],
            "engagement_name": record.engagement,
        }
        config = {
            "configurable": {
                "engagement_name": record.engagement,
                "workspace_path": str(workspace),
                "scan_mode": record.scan_mode,
            }
        }
        return state_input, config

    @staticmethod
    def _instruction(record: ScanRecord) -> str:
        if record.safe_mode:
            return (
                "Authorized engagement only. Prefer passive, non-destructive, "
                "read-only checks; do not perform exploitation."
            )
        return "Authorized engagement. Standard rules of engagement apply."

    # -- execution -------------------------------------------------------------
    def _run_blocking(self, record: ScanRecord) -> None:
        record.status = "running"
        record.started_at = time.time()
        record.current_phase = "starting"
        try:
            asyncio.run(self._arun(record))
        except Exception as exc:  # last-resort safety net
            record.error = record.error or f"Scan crashed: {exc}"
            record.status = "failed"
            record.current_phase = "failed"
            record.progress = 1.0
            record.completed_at = time.time()
            record.log(record.error)

    async def _arun(self, record: ScanRecord) -> None:
        timeout = _MODE_TIMEOUT.get(record.scan_mode, 1800)
        workspace = _engagement_workspace(record.engagement)
        try:
            workspace.mkdir(parents=True, exist_ok=True)
        except OSError as exc:
            record.log(f"Could not create workspace {workspace}: {exc}")

        record.current_phase = "building-agent"
        record.log(
            f"Building Decepticon '{self.role}' agent "
            "(the first scan in a process can take a while)..."
        )
        try:
            agent = self._get_agent()
        except Exception as exc:
            record.error = self._explain_failure(exc)
            record.log(f"Agent construction failed: {exc}")
            self._finalize(record, workspace)
            return

        state_input, config = self._build_invocation(record, workspace)
        record.current_phase = "running"
        record.log(
            f"Invoking Decepticon in-process "
            f"(engagement={record.engagement}, timeout={timeout}s)."
        )
        try:
            await asyncio.wait_for(
                self._stream(record, agent, state_input, config), timeout
            )
        except asyncio.TimeoutError:
            record.error = "Scan exceeded its time budget and was cancelled."
            record.log(record.error)
        except Exception as exc:
            record.error = self._explain_failure(exc)
            record.log(f"Scan run error: {exc}")

        self._finalize(record, workspace)

    async def _stream(
        self, record: ScanRecord, agent: Any, state_input: dict, config: dict
    ) -> None:
        async for item in agent.astream(
            state_input, config=config, stream_mode=["updates", "custom"]
        ):
            self._handle_stream_item(record, item)

    def _handle_stream_item(self, record: ScanRecord, item: Any) -> None:
        """Turn a LangGraph stream item into a phase update / log line."""
        if isinstance(item, tuple) and len(item) == 2:
            mode, chunk = item
        else:
            mode, chunk = None, item
        if mode == "updates" and isinstance(chunk, dict):
            for node, delta in chunk.items():
                record.current_phase = str(node)
                text = self._describe_update(node, delta)
                record.log(text)
        elif mode == "custom":
            record.log(f"event: {str(chunk)[:300]}")

    @staticmethod
    def _describe_update(node: Any, delta: Any) -> str:
        """Best-effort one-liner for an ``updates`` stream chunk."""
        snippet = ""
        if isinstance(delta, dict):
            messages = delta.get("messages")
            if isinstance(messages, list) and messages:
                last = messages[-1]
                content = getattr(last, "content", None)
                if content is None and isinstance(last, dict):
                    content = last.get("content")
                if isinstance(content, list):
                    content = " ".join(
                        str(p.get("text", "")) if isinstance(p, dict) else str(p)
                        for p in content
                    )
                snippet = str(content or "").strip().replace("\n", " ")[:200]
        return f"step: {node} {snippet}".strip()

    # -- finalization ----------------------------------------------------------
    def _finalize(self, record: ScanRecord, workspace: Path) -> None:
        findings = self._collect_findings(record, workspace)
        report = self._build_report(record, findings)
        graph_present = (workspace / "graph.json").exists()
        record.results = {
            "findings": findings,
            "attack_paths": [],  # Decepticon SARIF does not model chained paths
            "report": report,
            "scan_mode": record.scan_mode,
            "engagement": record.engagement,
            "agent_role": self.role,
            "sarif_present": graph_present,
        }
        record.progress = 1.0
        record.completed_at = time.time()

        if record.error:
            record.status = "failed"
            record.current_phase = "failed"
            record.log(f"Scan failed. {record.error}")
        else:
            record.status = "completed"
            record.current_phase = "completed"
            record.log(f"Scan completed; {len(findings)} finding(s).")

    _RUNTIME_HINT = (
        "Decepticon's backing services are not reachable. An in-process run "
        "needs an LLM provider/proxy (LLMFactory, default http://localhost:4000) "
        "and the bash sandbox; findings additionally need the KnowledgeGraph "
        "store (DECEPTICON_NEO4J_*). The library wiring is fine -- those "
        "services just aren't up yet."
    )
    _CONNECTION_SIGNATURES = (
        "all connection attempts failed",
        "getaddrinfo failed",
        "connection refused",
        "connection error",
        "connecterror",
        "apiconnectionerror",
        "llm proxy unreachable",
        "failed to establish",
        "no credentials detected",
        "localhost:4000",
        "max retries exceeded",
    )

    @classmethod
    def _explain_failure(cls, exc: Exception) -> str:
        text = f"{type(exc).__name__}: {exc}".lower()
        if any(sig in text for sig in cls._CONNECTION_SIGNATURES):
            return cls._RUNTIME_HINT
        return f"Decepticon run error ({type(exc).__name__}): {exc}"

    # -- findings (SARIF) ------------------------------------------------------
    def _collect_findings(self, record: ScanRecord, workspace: Path) -> list[dict]:
        """Export the engagement graph to SARIF and flatten it into findings.

        Uses Decepticon's own exporter; when no ``graph.json`` was persisted
        (e.g. the KnowledgeGraph store was not configured) this is zero
        findings, exactly as the upstream ``scan`` CLI reports.
        """
        graph_path = workspace / "graph.json"
        if not graph_path.exists():
            record.log(
                f"No graph.json at {graph_path}; treating as zero findings."
            )
            return []
        try:
            from decepticon_core.types.kg import KnowledgeGraph  # noqa: PLC0415
            from decepticon.tools.research.sarif_export import (  # noqa: PLC0415
                export_findings_to_sarif,
            )

            graph = KnowledgeGraph.from_json(graph_path.read_text(encoding="utf-8"))
            doc = export_findings_to_sarif(graph, engagement_name=record.engagement)
        except Exception as exc:  # noqa: BLE001
            record.log(f"Could not load/export findings graph: {exc}")
            return []
        return self._findings_from_sarif_doc(doc)

    def _findings_from_sarif_doc(self, doc: dict) -> list[dict]:
        findings: list[dict] = []
        for run in doc.get("runs", []):
            rules = self._rule_index(run)
            for i, result in enumerate(run.get("results", [])):
                findings.append(self._finding_from_result(result, rules, i))
        return findings

    @staticmethod
    def _rule_index(run: dict) -> dict[str, dict]:
        driver = run.get("tool", {}).get("driver", {})
        return {r.get("id"): r for r in driver.get("rules", []) if r.get("id")}

    def _finding_from_result(self, result: dict, rules: dict, index: int) -> dict:
        rule_id = result.get("ruleId") or f"finding_{index}"
        rule = rules.get(rule_id, {})
        message = (result.get("message") or {}).get("text", "") or rule.get(
            "shortDescription", {}
        ).get("text", "")
        props = result.get("properties", {}) or {}
        severity, risk = self._severity(result, props)
        locations = self._locations(result)
        remediation = (rule.get("help", {}) or {}).get("text", "") or (
            rule.get("fullDescription", {}) or {}
        ).get("text", "")

        return {
            "id": f"{rule_id}_{index}",
            "title": rule.get("name") or rule_id,
            "description": message,
            "severity": severity,
            "confidence": "",
            "technique_id": str(props.get("technique") or props.get("cwe") or ""),
            "tactic": str(props.get("tactic") or ""),
            "remediation": remediation,
            "risk_score": risk,
            "evidence": {"locations": locations} if locations else {},
        }

    @staticmethod
    def _severity(result: dict, props: dict) -> tuple[str, str]:
        raw = props.get("security-severity")
        if raw is not None:
            try:
                score = float(raw)
            except (TypeError, ValueError):
                score = None
            if score is not None:
                if score >= 9.0:
                    return "Critical", str(round(score * 10))
                if score >= 7.0:
                    return "High", str(round(score * 10))
                if score >= 4.0:
                    return "Medium", str(round(score * 10))
                if score > 0:
                    return "Low", str(round(score * 10))
                return "Info", "0"
        level = (result.get("level") or "warning").lower()
        return _LEVEL_SEVERITY.get(level, "Medium"), ""

    @staticmethod
    def _locations(result: dict) -> list[str]:
        out: list[str] = []
        for loc in result.get("locations", []):
            uri = (
                loc.get("physicalLocation", {})
                .get("artifactLocation", {})
                .get("uri")
            )
            if uri:
                out.append(uri)
        return out

    # -- report rendering ------------------------------------------------------
    def _build_report(self, record: ScanRecord, findings: list[dict]) -> dict:
        order = ["Critical", "High", "Medium", "Low", "Info"]
        counts = {sev: sum(1 for f in findings if f["severity"] == sev) for sev in order}

        exec_summary = (
            "EXECUTIVE SUMMARY\n=================\n\n"
            f"Target: {record.target}\n"
            f"Engagement: {record.engagement}\n"
            f"Scan Mode: {record.scan_mode}\n"
            f"Agent: {self.role}\n"
            f"Engine: Decepticon (PurpleAILAB), in-process library\n\n"
            f"Total Findings: {len(findings)}\n"
            + "\n".join(f"  {sev}: {counts[sev]}" for sev in order)
            + "\n"
        )

        if findings:
            tech_lines = ["TECHNICAL REPORT", "================", ""]
            for f in findings:
                loc = ", ".join(f.get("evidence", {}).get("locations", []))
                tech_lines.append(f"[{f['severity'].upper()}] {f['title']}")
                if loc:
                    tech_lines.append(f"    Location: {loc}")
                if f["description"]:
                    tech_lines.append(f"    {f['description']}")
                tech_lines.append("")
            technical = "\n".join(tech_lines)
            remediations = [f["remediation"] for f in findings if f["remediation"]]
            remediation = (
                "REMEDIATION GUIDE\n=================\n\n"
                + ("\n".join(f"- {r}" for r in remediations) or "No remediation guidance was provided by the scanner.")
            )
        else:
            technical = (
                "TECHNICAL REPORT\n================\n\n"
                "No findings were returned. If you expected results, confirm the "
                "Decepticon LLM/sandbox services are running and that the "
                "KnowledgeGraph store is configured, then check the Logs tab."
            )
            remediation = "REMEDIATION GUIDE\n=================\n\nNo findings to remediate."

        return {
            "executive_summary": exec_summary,
            "technical_report": technical,
            "remediation_guide": remediation,
        }

    # -- queries ---------------------------------------------------------------
    def get_record(self, scan_id: str) -> Optional[ScanRecord]:
        return self.scans.get(scan_id)

    def list_scans(self) -> list[dict]:
        records = sorted(self.scans.values(), key=lambda r: r.created_at, reverse=True)
        return [r.summary() for r in records]
