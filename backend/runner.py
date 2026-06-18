"""Thin adapter that drives the real ``decepticon`` CLI.

This module is the *only* glue between the OffensiveRed backend and the
upstream `Decepticon <https://github.com/PurpleAILAB/Decepticon>`_ framework.
It does not reimplement any scanning logic -- it shells out to the supported
``decepticon-cli scan`` entry point (run as ``python -m decepticon.cli scan``),
streams its JSONL events into a per-scan log, and parses the SARIF document the
CLI writes into the finding list the JavaFX GUI consumes.

The CLI routes the actual operation to Decepticon's LangGraph runtime
(``$DECEPTICON_API_URL``, default ``http://localhost:2024``) and an LLM proxy.
Those services, plus credentials, are the operator's responsibility; if they
are not reachable the CLI exits with a config error which is surfaced verbatim.
"""

from __future__ import annotations

import asyncio
import json
import os
import re
import subprocess
import sys
import tempfile
import threading
import time
import uuid
from dataclasses import dataclass, field
from pathlib import Path
from typing import Optional

# OffensiveRed scan scope -> Decepticon --scan-mode profile.
_SCOPE_TO_MODE = {
    "quick": "quick",
    "full": "deep",
    "web": "standard",
    "network": "standard",
    "api": "standard",
}

# Wall-clock caps per mode (seconds) so a scan can't leave a zombie process.
_MODE_TIMEOUT = {"quick": 600, "standard": 1800, "deep": 3600}

# SARIF result.level -> GUI severity label.
_LEVEL_SEVERITY = {
    "error": "High",
    "warning": "Medium",
    "note": "Low",
    "none": "Info",
}

_SCHEME_RE = re.compile(r"^[a-z][a-z0-9+.\-]*://", re.IGNORECASE)


def _normalize_target(target: str) -> str:
    """Make a GUI target acceptable to ``decepticon-cli --target``.

    The CLI treats a value without a URL/VCS scheme as a filesystem path and
    rejects it if it doesn't exist, so a bare host/domain is promoted to https.
    """
    t = target.strip()
    if _SCHEME_RE.match(t) or t.startswith(("git@", "ssh://")):
        return t
    if Path(t).exists():
        return t
    return f"https://{t}"


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
    returncode: Optional[int] = None
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
            "returncode": self.returncode,
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
    """Launches and tracks ``decepticon-cli scan`` subprocesses."""

    def __init__(self) -> None:
        self.scans: dict[str, ScanRecord] = {}
        self._tasks: dict[str, asyncio.Task] = {}

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
            f"(scan-mode={mode}, safe_mode={safe_mode})."
        )
        self.scans[scan_id] = record
        # The CLI is blocking; run it on a worker thread so it cooperates with
        # the event loop and works the same on every OS (no Proactor caveats).
        self._tasks[scan_id] = asyncio.create_task(
            asyncio.to_thread(self._run_blocking, record)
        )
        return scan_id

    def _build_command(self, record: ScanRecord, sarif_path: Path) -> list[str]:
        cmd = [
            sys.executable, "-m", "decepticon.cli", "scan",
            "--target", _normalize_target(record.target),
            "--scan-mode", record.scan_mode,
            "--non-interactive",
            "--sarif-output", str(sarif_path),
            "--engagement-name", record.engagement,
            "--fail-on", "none",  # findings shouldn't be reported as a failure
        ]
        if record.safe_mode:
            cmd += [
                "--instruction",
                "Authorized engagement only. Prefer passive, non-destructive, "
                "read-only checks; do not perform exploitation.",
            ]
        return cmd

    def _run_blocking(self, record: ScanRecord) -> None:
        record.status = "running"
        record.started_at = time.time()
        record.current_phase = "starting"
        timeout = _MODE_TIMEOUT.get(record.scan_mode, 1800)

        with tempfile.TemporaryDirectory(prefix="offensivered-") as tmp:
            sarif_path = Path(tmp) / "decepticon.sarif"
            cmd = self._build_command(record, sarif_path)
            record.log("Invoking: " + " ".join(cmd[2:]))  # hide python path noise

            try:
                proc = subprocess.Popen(
                    cmd,
                    stdout=subprocess.PIPE,
                    stderr=subprocess.STDOUT,
                    text=True,
                    bufsize=1,
                    env=os.environ.copy(),
                )
            except OSError as exc:
                record.status = "failed"
                record.error = f"Failed to launch decepticon-cli: {exc}"
                record.log(record.error)
                record.completed_at = time.time()
                return

            killer = threading.Timer(timeout, self._kill, args=(proc, record))
            killer.start()
            try:
                self._pump_output(proc, record)
                record.returncode = proc.wait()
            finally:
                killer.cancel()

            self._finalize(record, sarif_path)

    @staticmethod
    def _kill(proc: subprocess.Popen, record: ScanRecord) -> None:
        if proc.poll() is None:
            record.error = "Scan exceeded its time budget and was terminated."
            record.log(record.error)
            proc.kill()

    def _pump_output(self, proc: subprocess.Popen, record: ScanRecord) -> None:
        assert proc.stdout is not None
        for raw in proc.stdout:
            line = raw.rstrip()
            if not line:
                continue
            phase, text = self._interpret(line)
            if phase:
                record.current_phase = phase
            record.log(text)

    @staticmethod
    def _interpret(line: str) -> tuple[Optional[str], str]:
        """Turn a CLI stdout line (JSONL event or log text) into (phase, text)."""
        try:
            event = json.loads(line)
        except (ValueError, TypeError):
            return None, line[:500]
        if isinstance(event, dict) and "type" in event:
            etype = str(event.get("type"))
            data = event.get("data")
            snippet = ""
            if isinstance(data, dict):
                snippet = str(data.get("name") or data.get("node") or "")
            return (etype or None), f"event: {etype} {snippet}".strip()
        return None, line[:500]

    def _finalize(self, record: ScanRecord, sarif_path: Path) -> None:
        findings = self._parse_sarif(sarif_path, record)
        report = self._build_report(record, findings)
        record.results = {
            "findings": findings,
            "attack_paths": [],  # Decepticon SARIF does not model chained paths
            "report": report,
            "scan_mode": record.scan_mode,
            "engagement": record.engagement,
            "sarif_present": sarif_path.exists(),
        }
        record.progress = 1.0
        record.completed_at = time.time()
        rc = record.returncode

        if rc in (0, 1):
            record.status = "completed"
            record.current_phase = "completed"
            record.log(f"Scan completed (exit {rc}); {len(findings)} finding(s).")
        else:
            record.status = "failed"
            record.current_phase = "failed"
            if not record.error:
                record.error = self._explain_failure(rc, record.logs)
            record.log(f"Scan failed (exit {rc}). {record.error}")

    _RUNTIME_HINT = (
        "Decepticon's runtime is not reachable. A scan needs the Decepticon "
        "LangGraph server (DECEPTICON_API_URL, default http://localhost:2024) "
        "and an LLM provider/proxy running with valid credentials. The wiring "
        "is fine -- those backing services just aren't up yet."
    )
    _CONNECTION_SIGNATURES = (
        "all connection attempts failed",
        "getaddrinfo failed",
        "connection refused",
        "connecterror",
        "failed to establish",
        "no credentials detected",
    )

    @classmethod
    def _explain_failure(cls, rc: Optional[int], logs: list[str]) -> str:
        recent = " ".join(logs[-40:]).lower()
        if any(sig in recent for sig in cls._CONNECTION_SIGNATURES):
            return cls._RUNTIME_HINT
        if rc == 2:
            return (
                "Decepticon reported a configuration/invocation error (see logs)."
            )
        if rc == 3:
            return "Decepticon hit an internal error during the scan (see logs)."
        return f"decepticon-cli exited with status {rc} (see logs)."

    # -- SARIF parsing ---------------------------------------------------------
    def _parse_sarif(self, sarif_path: Path, record: ScanRecord) -> list[dict]:
        if not sarif_path.exists():
            return []
        try:
            doc = json.loads(sarif_path.read_text(encoding="utf-8"))
        except (ValueError, OSError) as exc:
            record.log(f"Could not parse SARIF output: {exc}")
            return []

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
            f"Engine: Decepticon (PurpleAILAB)\n\n"
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
                "Decepticon runtime is running and check the Logs tab."
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
