package com.redsecai.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Response from {@code POST /scan/start}.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ScanResponse {
    private String scanId;
    private String status;
    private String message;
    
    public ScanResponse() {}
    
    public String getScanId() {
        return scanId;
    }
    
    public void setScanId(String scanId) {
        this.scanId = scanId;
    }
    
    public String getStatus() {
        return status;
    }
    
    public void setStatus(String status) {
        this.status = status;
    }
    
    public String getMessage() {
        return message;
    }
    
    public void setMessage(String message) {
        this.message = message;
    }
}
