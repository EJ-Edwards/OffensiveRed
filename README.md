# RedSecAI - Adversary Emulation System

A production-ready, AI-driven adversary emulation system that simulates advanced attacker thinking based on MITRE ATT&CK concepts, but executes only safe, non-destructive checks.

## ⚠️ IMPORTANT SAFETY NOTICE

This system is designed for **AUTHORIZED, NON-DESTRUCTIVE, and SAFE security testing only**.
- No real exploitation payloads
- No harmful actions
- No bypass techniques
- Only safe, permission-based testing logic
- Simulation, analysis, and risk modeling only

## System Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                     RedSecAI System Architecture                  │
└─────────────────────────────────────────────────────────────────┘

┌──────────────────┐
│   JavaFX GUI     │
│   (Frontend)     │
└────────┬─────────┘
         │ HTTP/REST API
         ↓
┌─────────────────────────────────────────────────────────────────┐
│                     FastAPI Backend                              │
└─────────────────────────────────────────────────────────────────┘
         │
         ↓
┌─────────────────────────────────────────────────────────────────┐
│                 Master Orchestrator Agent                        │
│  - Controls full workflow                                        │
│  - Delegates tasks to sub-agents                                 │
│  - Tracks scan state and progress                                │
└─────────────────────────────────────────────────────────────────┘
         │
         ↓
┌─────────────────────────────────────────────────────────────────┐
│              LangGraph-Style Workflow Graph                      │
│  - Graph-based node execution                                   │
│  - Async execution support                                      │
│  - Dependency management                                        │
│  - Retry + error handling                                       │
└─────────────────────────────────────────────────────────────────┘
         │
    ┌────┼──────────────────────────────────────────────────┐
    ↓    ↓    ↓    ↓    ↓    ↓    ↓
┌────┐┌────┐┌────┐┌────┐┌────┐┌────┐┌────┐┐
│Plan││Recon││Scan││Attack││Verif││Risk││Report│
│ner ││    ││ner││Path ││icat ││Score││er   │
└────┘└────┘└────┘└────┘└────┘└────┘└────┘┘

┌─────────────────────────────────────────────────────────────────┐
│                    Modular Scanner Tools                         │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐                      │
│  │Web Scan  │  │API Scan  │  │Cloud Scan │                      │
│  └──────────┘  └──────────┘  └──────────┘                      │
└─────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────┐
│                   MITRE ATT&CK Framework                        │
│  - Tactics: Recon, Initial Access, Execution, etc.              │
│  - Techniques: Mapped to safe simulation methods                 │
│  - Risk Levels: Low, Medium, High                               │
└─────────────────────────────────────────────────────────────────┘
```

## Components

### 1. Master Orchestrator Agent
- Controls the full workflow
- Delegates tasks to sub-agents
- Tracks scan state and progress
- Manages scan history

### 2. Planner Agent
- Takes user input (target, scope)
- Maps to MITRE-style tactics
- Generates structured scan plan
- Estimates scan duration

### 3. Recon Agent (SAFE)
- Non-intrusive discovery only
- Subdomain enumeration
- DNS lookups
- Public metadata analysis
- No aggressive scanning

### 4. Scanner Agent
- Calls Python-based scanning tools
- Modular tool plugins
- Web checks (auth, input handling)
- API checks
- Cloud misconfiguration checks

### 5. Attack Path Simulator
- DOES NOT exploit
- Simulates how findings could be chained
- Example: "Weak auth + exposed endpoint → possible data exposure risk"
- Generates attack path visualizations

### 6. Verification Agent
- Re-checks findings
- Reduces false positives
- Assigns confidence score (low/medium/high)
- Deduplicates findings

### 7. Risk Scoring Engine
- Combines technical severity
- Incorporates business impact context
- Outputs score 0–100
- Risk level categorization

### 8. Report Agent
- Executive summary (non-technical)
- Technical breakdown
- Clear remediation steps
- Prioritized action items

## Workflow Execution

1. **Planning Phase**
   - User provides target and scope
   - Planner maps to MITRE tactics
   - Generates structured plan

2. **Reconnaissance Phase**
   - Safe DNS lookups
   - Subdomain enumeration
   - Public metadata analysis

3. **Scanning Phase**
   - Web security checks
   - API security checks
   - Cloud misconfiguration checks

4. **Attack Path Simulation**
   - Chains findings into potential attack paths
   - Simulates attacker thinking
   - No actual exploitation

5. **Verification Phase**
   - Re-checks findings
   - Assigns confidence scores
   - Removes duplicates

6. **Risk Scoring**
   - Calculates individual finding scores
   - Computes overall risk score
   - Categorizes risk level

7. **Reporting**
   - Generates executive summary
   - Creates technical report
   - Provides remediation guide

## Installation

### Backend (Python)

```bash
cd backend
pip install -r requirements.txt
```

### Frontend (JavaFX)

```bash
cd frontend
mvn clean package
```

## Usage

### Start Backend API

```bash
cd backend
python -m backend.api.main
```

The API will start on `http://localhost:8000`

### Start GUI Frontend

```bash
cd frontend
mvn javafx:run
```

Or run the packaged JAR:
```bash
java -jar target/redsecai-gui-1.0.0.jar
```

## API Endpoints

- `POST /scan/start` - Start a new scan
- `GET /scan/status/{scan_id}` - Get scan status
- `GET /scan/result/{scan_id}` - Get scan results
- `GET /scans` - List all scans
- `GET /workflow/status` - Get workflow status
- `GET /mitre/techniques` - Get MITRE ATT&CK techniques
- `GET /health` - Health check

## Configuration

### Backend Configuration

Edit `backend/api/main.py` to configure:
- Host and port
- CORS settings
- Logging levels

### Frontend Configuration

Edit `frontend/src/main/java/com/redsecai/service/ApiService.java` to configure:
- Backend API URL
- Timeout settings

## Safety Features

1. **Safe Mode Default**: All scans default to safe mode
2. **Non-Intrusive Recon**: No aggressive scanning
3. **Simulation Only**: Attack path simulator does not exploit
4. **Permission-Based**: Requires explicit authorization
5. **No Payloads**: No real exploitation payloads
6. **Logging**: All actions logged for audit

## Sample Output

### Executive Summary
```
Overall Risk Score: 65/100
Risk Level: Medium
Total Findings: 5
Key Recommendations:
- Enable SSL/TLS encryption
- Implement security headers
- Strengthen authentication
```

### Technical Report
```
Scan ID: abc-123-def
Target: example.com
Findings by Severity:
- Critical: 0
- High: 2
- Medium: 2
- Low: 1
```

### Attack Path Example
```
Path: Authentication Weakness → Data Exposure
Step 1: Attacker identifies weak authentication controls (T1078)
Step 2: Attacker accesses exposed data endpoints (T1005)
Likelihood: Medium
Impact: High
Overall Risk: 75/100
```

## Technology Stack

### Backend
- Python 3.8+
- FastAPI
- Asyncio
- DNSPython
- aiohttp

### Frontend
- Java 17
- JavaFX 17
- Maven
- Jackson (JSON)

### Architecture
- Graph-based workflow (LangGraph-style)
- Async execution
- REST API communication
- MITRE ATT&CK framework

## License

This system is designed for authorized security testing only. Use responsibly and only with proper authorization.

## Support

For issues and questions, please refer to the documentation or contact the development team.
