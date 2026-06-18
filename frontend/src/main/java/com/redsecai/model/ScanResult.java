package com.redsecai.model;

import java.util.Map;

/**
 * Model for scan result
 */
public class ScanResult {
    private String scanId;
    private String target;
    private String status;
    private Map<String, Object> results;
    
    public ScanResult() {}
    
    public String getScanId() {
        return scanId;
    }
    
    public void setScanId(String scanId) {
        this.scanId = scanId;
    }
    
    public String getTarget() {
        return target;
    }
    
    public void setTarget(String target) {
        this.target = target;
    }
    
    public String getStatus() {
        return status;
    }
    
    public void setStatus(String status) {
        this.status = status;
    }
    
    public Map<String, Object> getResults() {
        return results;
    }
    
    public void setResults(Map<String, Object> results) {
        this.results = results;
    }
}
