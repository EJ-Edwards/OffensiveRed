# RedSecAI Usage Guide

## Quick Start

### 1. Start the Backend

```bash
cd backend
pip install -r requirements.txt
python -m backend.api.main
```

The backend API will start on `http://localhost:8000`

### 2. Start the GUI

```bash
cd frontend
mvn clean package
mvn javafx:run
```

Or run the JAR:
```bash
java -jar target/redsecai-gui-1.0.0.jar
```

### 3. Run a Scan

1. Enter a target URL or domain (e.g., `example.com`)
2. Select scan scope:
   - **Full Scan**: Comprehensive assessment
   - **Quick Scan**: Fast overview
   - **Web Scan**: Web application focus
   - **Network Scan**: Network infrastructure focus
   - **API Scan**: API security focus
3. Ensure "Safe Mode" is checked (default)
4. Click "Start Scan"
5. Monitor progress in the "Logs" tab
6. View results in "Findings", "Attack Paths", and "Reports" tabs

## API Usage

### Start a Scan

```bash
curl -X POST http://localhost:8000/scan/start \
  -H "Content-Type: application/json" \
  -d '{
    "target": "example.com",
    "scope": ["full"],
    "safe_mode": true
  }'
```

Response:
```json
{
  "scan_id": "abc-123-def",
  "status": "started",
  "message": "Scan started for example.com"
}
```

### Check Scan Status

```bash
curl http://localhost:8000/scan/status/abc-123-def
```

### Get Scan Results

```bash
curl http://localhost:8000/scan/result/abc-123-def
```

### List All Scans

```bash
curl http://localhost:8000/scans
```

### Get MITRE Techniques

```bash
curl http://localhost:8000/mitre/techniques
```

## Scan Scope Options

### Full Scan
- All tactics and techniques
- Comprehensive reconnaissance
- Web, API, and cloud checks
- Estimated duration: 30-60 minutes

### Quick Scan
- Core tactics only
- Basic reconnaissance
- Essential security checks
- Estimated duration: 10-15 minutes

### Web Scan
- Web application focus
- Authentication checks
- Security headers
- Input handling
- SSL/TLS verification

### Network Scan
- Network infrastructure focus
- DNS analysis
- Service discovery
- Network mapping

### API Scan
- API security focus
- Endpoint discovery
- Documentation exposure
- API-specific checks

## Understanding Results

### Executive Summary
- **Overall Risk Score**: 0-100 (higher = more risk)
- **Risk Level**: Minimal, Low, Medium, High, Critical
- **Total Findings**: Count of all findings
- **Key Recommendations**: Top priority actions

### Technical Report
- **Findings by Severity**: Breakdown by critical/high/medium/low
- **Findings by Tactic**: Grouped by MITRE ATT&CK tactic
- **Attack Paths Summary**: Simulated attack chains
- **Risk Distribution**: Statistical breakdown

### Attack Paths
Each attack path shows:
- **Step-by-step progression**: How an attacker could chain findings
- **MITRE Technique IDs**: Reference to ATT&CK framework
- **Likelihood**: Probability of occurrence
- **Impact**: Potential business impact
- **Overall Risk**: Calculated risk score

### Remediation Guide
Prioritized into three categories:
- **Immediate Action Required**: Critical/high risk items
- **Short-term Remediation**: Medium risk items
- **Long-term Improvements**: Low risk items and best practices

## Example Output

### Executive Summary
```
Overall Risk Score: 65/100
Risk Level: Medium
Total Findings: 5
Critical Findings: 0
High Findings: 2
Medium Findings: 2
Low Findings: 1

Key Recommendations:
- Enable SSL/TLS encryption for all services
- Strengthen authentication mechanisms and implement MFA
- Implement security headers to protect against common attacks

Business Impact:
High-risk security issues present that could impact business operations. 
Remediation should be prioritized within the next 30 days.
```

### Attack Path Example
```
Path: Authentication Weakness → Data Exposure

Step 1: Attacker identifies weak authentication controls
Finding: Unprotected Path: /admin
Technique: T1078 - Valid Accounts

Step 2: Attacker accesses exposed data endpoints without proper authentication
Finding: API Endpoint Discovered: /api/v1
Technique: T1005 - Data from Local System

Likelihood: Medium
Impact: High
Overall Risk: 75/100
```

### Finding Example
```
ID: missing_header_X-Frame-Options
Title: Missing Security Header: X-Frame-Options
Description: The X-Frame-Options header is not set. This header provides 
Clickjacking protection.
Severity: Low
Confidence: High
Technique ID: T1190
Tactic: Initial Access
Evidence: {"missing_header": "X-Frame-Options"}
Remediation: Add the X-Frame-Options header to your web server configuration
Risk Score: 25.0
```

## Best Practices

1. **Always use Safe Mode**: Unless you have explicit authorization
2. **Start with Quick Scan**: Get an overview before running full scans
3. **Review Executive Summary First**: Understand business impact
4. **Prioritize Immediate Actions**: Address critical/high findings first
5. **Document Remediation**: Track your progress on fixing issues
6. **Regular Scans**: Schedule periodic security assessments
7. **Compare Results**: Track security posture over time

## Troubleshooting

### Backend Won't Start
- Check if port 8000 is already in use
- Verify Python dependencies are installed
- Check logs for error messages

### GUI Won't Connect to Backend
- Ensure backend is running on `http://localhost:8000`
- Check firewall settings
- Verify API service URL in `ApiService.java`

### Scan Times Out
- Check network connectivity to target
- Verify target is accessible
- Try with "Quick Scan" scope first
- Check backend logs for errors

### No Findings Returned
- Target may be well-secured
- Check if target is accessible
- Verify scope selection
- Review logs for errors

## Safety Reminders

⚠️ **IMPORTANT**: This system is for authorized security testing only.

- Only scan targets you own or have explicit permission to test
- Never use for malicious purposes
- Respect rate limits and avoid overloading targets
- Keep all findings confidential
- Follow responsible disclosure practices

## Support

For issues or questions:
1. Check the README.md for architecture details
2. Review ARCHITECTURE.md for system design
3. Check logs for error messages
4. Contact the development team if needed
