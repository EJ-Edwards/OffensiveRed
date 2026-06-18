package com.redsecai.model;

/**
 * Model for scan response
 */
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
