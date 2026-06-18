package com.redsecai.model;

import java.util.List;

/**
 * Model for scan request
 */
public class ScanRequest {
    private String target;
    private List<String> scope;
    private boolean safeMode;
    
    public ScanRequest() {}
    
    public String getTarget() {
        return target;
    }
    
    public void setTarget(String target) {
        this.target = target;
    }
    
    public List<String> getScope() {
        return scope;
    }
    
    public void setScope(List<String> scope) {
        this.scope = scope;
    }
    
    public boolean isSafeMode() {
        return safeMode;
    }
    
    public void setSafeMode(boolean safeMode) {
        this.safeMode = safeMode;
    }
}
