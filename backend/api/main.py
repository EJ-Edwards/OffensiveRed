"""FastAPI application for the OffensiveRed / RedSecAI backend.

A thin HTTP shell the JavaFX GUI talks to. Every scan is executed by the real
``decepticon`` library in-process via :mod:`backend.runner`; this layer only
translates GUI requests into library runs and the resulting findings back into
JSON.

Run from the repository root with::

    python -m backend.api.main

or::

    uvicorn backend.api.main:app --host 127.0.0.1 --port 8000
"""

from __future__ import annotations

from fastapi import FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware

from ..runner import DecepticonRunner
from .models import ScanResultResponse, ScanStartRequest, ScanStartResponse

app = FastAPI(
    title="OffensiveRed / RedSecAI",
    description="HTTP front-end that drives the Decepticon autonomous red-team "
    "framework (https://github.com/PurpleAILAB/Decepticon).",
    version="1.0.0",
)

# The GUI is a desktop app, but CORS is enabled for local browser tooling too.
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=False,
    allow_methods=["*"],
    allow_headers=["*"],
)

runner = DecepticonRunner()


@app.get("/health")
async def health() -> dict:
    return {
        "status": "ok",
        "engine": "decepticon",
        "integration": "library",
        "agent": runner.role,
        "version": app.version,
    }


@app.post("/scan/start", response_model=ScanStartResponse)
async def start_scan(request: ScanStartRequest) -> ScanStartResponse:
    target = request.target.strip()
    if not target:
        raise HTTPException(status_code=400, detail="A target is required.")
    scan_id = await runner.start_scan(target, request.scope, request.safe_mode)
    return ScanStartResponse(
        scan_id=scan_id,
        status="started",
        message=f"Decepticon scan started for {target}",
    )


@app.get("/scan/status/{scan_id}")
async def scan_status(scan_id: str) -> dict:
    record = runner.get_record(scan_id)
    if record is None:
        raise HTTPException(status_code=404, detail="Scan not found.")
    return record.status_view()


@app.get("/scan/result/{scan_id}", response_model=ScanResultResponse)
async def scan_result(scan_id: str) -> ScanResultResponse:
    record = runner.get_record(scan_id)
    if record is None:
        raise HTTPException(status_code=404, detail="Scan not found.")
    view = record.result_view()
    return ScanResultResponse(
        scan_id=view["scan_id"],
        target=view["target"],
        status=view["status"],
        results=view["results"],
    )


@app.get("/scans")
async def list_scans() -> dict:
    return {"scans": runner.list_scans()}


@app.get("/workflow/status")
async def workflow_status() -> dict:
    records = runner.list_scans()
    active = records[0] if records else None
    return {
        "engine": "decepticon",
        "active_scan": active["scan_id"] if active else None,
        "status": active["status"] if active else "idle",
        "current_phase": active["current_phase"] if active else None,
        "progress": active["progress"] if active else 0.0,
    }


def main() -> None:
    import uvicorn

    uvicorn.run(app, host="127.0.0.1", port=8000)


if __name__ == "__main__":
    main()
