package com.redsecai.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Live status snapshot from {@code GET /scan/status/{id}}.
 *
 * <p>That endpoint returns the runner's raw {@code snake_case} view (unlike
 * {@code /scan/start} and {@code /scan/result}, which use camelCase), so the
 * one non-matching field is mapped explicitly and unknown fields are ignored.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ScanStatus {
    private String status = "";
    @JsonProperty("current_phase")
    private String currentPhase = "";
    private double progress;
    private String error;
    private List<String> logs;

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getCurrentPhase() {
        return currentPhase;
    }

    public void setCurrentPhase(String currentPhase) {
        this.currentPhase = currentPhase;
    }

    public double getProgress() {
        return progress;
    }

    public void setProgress(double progress) {
        this.progress = progress;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }

    public List<String> getLogs() {
        return logs;
    }

    public void setLogs(List<String> logs) {
        this.logs = logs;
    }

    /** True once the backend has reached a terminal state. */
    public boolean isTerminal() {
        return "completed".equals(status) || "failed".equals(status);
    }
}
