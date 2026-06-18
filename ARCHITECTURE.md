# RedSecAI System Architecture

## Overview

RedSecAI is a modular, scalable AI agent system that simulates advanced attacker thinking based on MITRE ATT&CK concepts, but executes only safe, non-destructive checks.

## Architecture Diagram

```
┌─────────────────────────────────────────────────────────────────┐
│                        User Interface                            │
│                    JavaFX GUI Application                        │
│  - Target Input                                                 │
│  - Scope Selection                                               │
│  - Real-time Progress                                           │
│  - Findings Display                                              │
│  - Attack Path Visualization                                     │
│  - Report Generation                                            │
└─────────────────────────────────────────────────────────────────┘
                              │
                              │ HTTP/REST API
                              │
┌─────────────────────────────────────────────────────────────────┐
│                      API Layer (FastAPI)                         │
│  - /scan/start                                                   │
│  - /scan/status/{id}                                             │
│  - /scan/result/{id}                                             │
│  - /workflow/status                                             │
│  - /mitre/techniques                                            │
└─────────────────────────────────────────────────────────────────┘
                              │
                              │
┌─────────────────────────────────────────────────────────────────┐
│                 Master Orchestrator Agent                        │
│  - Workflow coordination                                         │
│  - Agent registration                                            │
│  - State management                                              │
│  - Scan history tracking                                         │
└─────────────────────────────────────────────────────────────────┘
                              │
                              │
┌─────────────────────────────────────────────────────────────────┐
│              Workflow Graph (LangGraph-style)                     │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │  Node: Planner                                          │   │
│  │  State: Running → Completed                              │   │
│  │  Dependencies: None                                       │   │
│  └──────────────────────────────────────────────────────────┘   │
│                              │                                    │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │  Node: Recon                                             │   │
│  │  State: Running → Completed                              │   │
│  │  Dependencies: Planner                                   │   │
│  └──────────────────────────────────────────────────────────┘   │
│                              │                                    │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │  Node: Scanner                                           │   │
│  │  State: Running → Completed                              │   │
│  │  Dependencies: Recon                                     │   │
│  └──────────────────────────────────────────────────────────┘   │
│                              │                                    │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │  Node: Attack Path                                       │   │
│  │  State: Running → Completed                              │   │
│  │  Dependencies: Scanner                                    │   │
│  └──────────────────────────────────────────────────────────┘   │
│                              │                                    │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │  Node: Verification                                      │   │
│  │  State: Running → Completed                              │   │
│  │  Dependencies: Scanner, Attack Path                      │   │
│  └──────────────────────────────────────────────────────────┘   │
│                              │                                    │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │  Node: Risk Scorer                                       │   │
│  │  State: Running → Completed                              │   │
│  │  Dependencies: Verification                               │   │
│  └──────────────────────────────────────────────────────────┘   │
│                              │                                    │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │  Node: Reporter                                           │   │
│  │  State: Running → Completed                              │   │
│  │  Dependencies: Risk Scorer                                │   │
│  └──────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────┘
                              │
                              │
┌─────────────────────────────────────────────────────────────────┐
│                    Specialized Agents                            │
│                                                                  │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐          │
│  │  Planner     │  │  Recon       │  │  Scanner     │          │
│  │  Agent       │  │  Agent       │  │  Agent       │          │
│  │              │  │              │  │              │          │
│  │ - MITRE map  │  │ - DNS lookup │  │ - Web checks │          │
│  │ - Plan gen   │  │ - Subdomains │  │ - API checks │          │
│  │ - Duration   │  │ - Metadata   │  │ - Cloud      │          │
│  └──────────────┘  └──────────────┘  └──────────────┘          │
│                                                                  │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐          │
│  │  Attack Path │  │Verification  │  │  Risk Scorer │          │
│  │  Agent       │  │  Agent       │  │  Agent       │          │
│  │              │  │              │  │              │          │
│  │ - Chain      │  │ - Re-check   │  │ - Score calc │          │
│  │ - Simulate   │  │ - Confidence │  │ - Risk level │          │
│  │ - Visualize  │  │ - Dedup      │  │ - Distribution│         │
│  └──────────────┘  └──────────────┘  └──────────────┘          │
│                                                                  │
│  ┌──────────────┐                                               │
│  │  Reporter    │                                               │
│  │  Agent       │                                               │
│  │              │                                               │
│  │ - Executive  │                                               │
│  │ - Technical  │                                               │
│  │ - Remediation│                                               │
│  └──────────────┘                                               │
└─────────────────────────────────────────────────────────────────┘
                              │
                              │
┌─────────────────────────────────────────────────────────────────┐
│                    Scanner Tools (Plugins)                       │
│  ┌──────────────────┐  ┌──────────────────┐  ┌──────────────┐   │
│  │  Web Scanner     │  │  API Scanner     │  │Cloud Scanner │   │
│  │                  │  │                  │  │              │   │
│  │ - Auth checks    │  │ - Endpoint disc  │  │ - Provider  │   │
│  │ - Header checks  │  │ - Doc exposure   │  │ - Config    │   │
│  │ - Input handling │  │ - Security       │  │ - Review    │   │
│  │ - SSL/TLS        │  │                  │  │              │   │
│  └──────────────────┘  └──────────────────┘  └──────────────┘   │
└─────────────────────────────────────────────────────────────────┘
                              │
                              │
┌─────────────────────────────────────────────────────────────────┐
│                  MITRE ATT&CK Framework                          │
│                                                                  │
│  Tactics:                                                        │
│  - Reconnaissance                                                │
│  - Resource Development                                          │
│  - Initial Access                                                │
│  - Execution                                                     │
│  - Persistence                                                   │
│  - Privilege Escalation                                          │
│  - Defense Evasion                                               │
│  - Credential Access                                             │
│  - Discovery                                                     │
│  - Lateral Movement                                              │
│  - Collection                                                    │
│  - Command and Control                                           │
│  - Exfiltration                                                  │
│  - Impact                                                        │
│                                                                  │
│  Each technique has:                                              │
│  - Technique ID                                                  │
│  - Safe simulation method                                        │
│  - Risk level (low/medium/high)                                 │
└─────────────────────────────────────────────────────────────────┘
```

## Data Flow

1. **User Input** → GUI → API Request
2. **API** → Master Orchestrator → Workflow Graph
3. **Workflow** → Planner Agent → Scan Plan
4. **Workflow** → Recon Agent → Recon Data
5. **Workflow** → Scanner Agent → Findings (via Tools)
6. **Workflow** → Attack Path Agent → Attack Paths
7. **Workflow** → Verification Agent → Verified Findings
8. **Workflow** → Risk Scorer Agent → Risk Scores
9. **Workflow** → Reporter Agent → Reports
10. **Results** → API → GUI Display

## Key Design Principles

1. **Safety First**: All operations are non-destructive
2. **Modularity**: Each agent is independent and reusable
3. **Scalability**: Async execution allows parallel processing
4. **Extensibility**: Easy to add new agents and tools
5. **Observability**: Comprehensive logging at every step
6. **Resilience**: Retry logic and error handling
7. **Business-Friendly**: Clear reports for non-technical users

## Technology Stack

### Backend
- **Python 3.8+**: Core language
- **FastAPI**: REST API framework
- **Asyncio**: Async execution
- **DNSPython**: DNS operations
- **aiohttp**: HTTP client

### Frontend
- **Java 17**: Language
- **JavaFX 17**: GUI framework
- **Maven**: Build tool
- **Jackson**: JSON processing

### Architecture
- **Graph-based workflow**: LangGraph-style node execution
- **MITRE ATT&CK**: Security framework
- **REST API**: Communication protocol

## Security Considerations

1. **No Exploitation**: System never exploits vulnerabilities
2. **Safe Mode Default**: All scans default to safe mode
3. **Non-Intrusive**: Recon is passive and non-aggressive
4. **Simulation**: Attack paths are simulated, not executed
5. **Authorization**: Requires explicit permission for scanning
6. **Audit Trail**: All actions logged for accountability
