package com.redsecai.controller;

import com.redsecai.model.Finding;
import com.redsecai.model.ScanRequest;
import com.redsecai.model.ScanResponse;
import com.redsecai.model.ScanResult;
import com.redsecai.model.ScanStatus;
import com.redsecai.model.Severity;
import com.redsecai.service.ApiService;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;

import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Drives the RedSecAI GUI: starts engagements, streams live status/logs from
 * the backend, and renders findings and reports once a run reaches a terminal
 * state.
 */
public class MainController implements Initializable {

    private static final String BACKEND_URL = "http://localhost:8000";
    /** Poll cadence for the live status endpoint. */
    private static final long POLL_INTERVAL_MS = 1500;
    /** Safety cap on how long the GUI will watch a single run (deep scans are long). */
    private static final long WATCH_CAP_MS = 90 * 60 * 1000L;
    /** Give up watching after this many consecutive failed polls. */
    private static final int MAX_CONSECUTIVE_ERRORS = 12;

    @FXML private Label backendStatusLabel;

    @FXML private TextField targetTextField;
    @FXML private ComboBox<String> scopeComboBox;
    @FXML private CheckBox safeModeCheckBox;
    @FXML private Button startScanButton;
    @FXML private Button cancelButton;

    @FXML private Label statusDot;
    @FXML private Label statusLabel;
    @FXML private ProgressBar progressBar;
    @FXML private TabPane mainTabPane;

    @FXML private Label countCritical;
    @FXML private Label countHigh;
    @FXML private Label countMedium;
    @FXML private Label countLow;
    @FXML private Label countInfo;
    @FXML private Label countTotal;

    @FXML private Label ovTarget;
    @FXML private Label ovEngagement;
    @FXML private Label ovAgent;
    @FXML private Label ovScanMode;
    @FXML private Label ovStatus;

    @FXML private ListView<Finding> findingsListView;
    @FXML private TextArea executiveSummaryTextArea;
    @FXML private TextArea technicalReportTextArea;
    @FXML private TextArea remediationTextArea;
    @FXML private TextArea logTextArea;

    private ApiService apiService;
    private ScheduledExecutorService healthExecutor;
    private Task<Void> currentTask;
    private String currentScanId;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        apiService = new ApiService(BACKEND_URL);

        scopeComboBox.setItems(FXCollections.observableArrayList(
            "Full Scan", "Quick Scan", "Web Scan", "Network Scan", "API Scan"));
        scopeComboBox.getSelectionModel().selectFirst();

        findingsListView.setCellFactory(list -> new FindingListCell(findingsListView));
        findingsListView.setPlaceholder(new Label("No findings yet — start an engagement."));

        setBackendOnline(false);
        setScanControlsBusy(false);
        setStatus("idle", "Idle — configure a target and start", 0);
        resetResults();

        startHealthChecks();
    }

    // ----------------------------------------------------------------- actions

    @FXML
    private void startScan() {
        String target = targetTextField.getText().trim();
        if (target.isEmpty()) {
            showWarning("Target required", "Enter a URL, domain, or repository you are authorized to test.");
            return;
        }

        String uiScope = scopeComboBox.getSelectionModel().getSelectedItem();
        boolean safeMode = safeModeCheckBox.isSelected();

        setScanControlsBusy(true);
        resetResults();
        ovTarget.setText(target);
        ovScanMode.setText(mapScopeToMode(uiScope));
        ovStatus.setText("starting");
        setStatus("running", "Submitting engagement…", System.currentTimeMillis());

        final long startedAt = System.currentTimeMillis();
        currentTask = new Task<>() {
            @Override
            protected Void call() {
                try {
                    ScanRequest request = new ScanRequest();
                    request.setTarget(target);
                    request.setScope(List.of(mapScopeToBackend(uiScope)));
                    request.setSafeMode(safeMode);

                    ScanResponse response = apiService.startScan(request);
                    currentScanId = response.getScanId();
                    watchScan(startedAt);
                } catch (Exception e) {
                    Platform.runLater(() -> {
                        setStatus("failed", "Could not start engagement", startedAt);
                        setScanControlsBusy(false);
                        showError("Failed to start engagement", e.getMessage());
                    });
                }
                return null;
            }
        };

        Thread thread = new Thread(currentTask, "scan-watcher");
        thread.setDaemon(true);
        thread.start();
    }

    @FXML
    private void cancelScan() {
        if (currentTask != null && currentTask.isRunning()) {
            currentTask.cancel();
        }
        setScanControlsBusy(false);
        setStatus("idle", "Stopped watching — the backend run continues", 0);
    }

    // --------------------------------------------------------------- polling

    /** Polls the status endpoint until the run is terminal, cancelled, or capped. */
    private void watchScan(long startedAt) {
        int consecutiveErrors = 0;
        while (!currentTask.isCancelled()) {
            if (System.currentTimeMillis() - startedAt > WATCH_CAP_MS) {
                Platform.runLater(() -> {
                    setStatus("failed", "Stopped watching after " + (WATCH_CAP_MS / 60000) + " min", startedAt);
                    setScanControlsBusy(false);
                    showWarning("Still running",
                        "The GUI stopped watching after " + (WATCH_CAP_MS / 60000)
                            + " minutes. The backend run may still be going — check the Logs tab.");
                });
                return;
            }

            ScanStatus status;
            try {
                status = apiService.getScanStatus(currentScanId);
                consecutiveErrors = 0;
            } catch (Exception e) {
                if (++consecutiveErrors >= MAX_CONSECUTIVE_ERRORS) {
                    Platform.runLater(() -> {
                        setStatus("failed", "Lost contact with backend", startedAt);
                        setScanControlsBusy(false);
                        showError("Backend unreachable", "Stopped after repeated errors: " + e.getMessage());
                    });
                    return;
                }
                sleepQuietly();
                continue;
            }

            Platform.runLater(() -> applyStatus(status, startedAt));

            if (status.isTerminal()) {
                loadResults(startedAt);
                return;
            }
            sleepQuietly();
        }
    }

    /** Reflect a live status snapshot in the run bar, progress, and logs. */
    private void applyStatus(ScanStatus status, long startedAt) {
        String phase = firstNonEmpty(status.getCurrentPhase(), status.getStatus());
        setStatus(status.getStatus(), phase, startedAt);
        ovStatus.setText(status.getStatus());

        if ("completed".equals(status.getStatus())) {
            progressBar.setProgress(1.0);
        } else if ("failed".equals(status.getStatus())) {
            progressBar.setProgress(0);
        } else {
            progressBar.setProgress(ProgressBar.INDETERMINATE_PROGRESS);
        }

        if (status.getLogs() != null) {
            logTextArea.setText(String.join("\n", status.getLogs()));
            logTextArea.setScrollTop(Double.MAX_VALUE);
        }
    }

    /** Fetch and render findings + reports after a run reaches a terminal state. */
    private void loadResults(long startedAt) {
        try {
            ScanResult result = apiService.getScanResult(currentScanId);
            Map<String, Object> results = result.getResults();

            List<Finding> findings = new ArrayList<>();
            for (Map<String, Object> raw : extractList(result, "findings")) {
                findings.add(Finding.fromMap(raw));
            }
            findings.sort(Comparator.comparingInt(f -> f.severity().rank()));

            boolean completed = "completed".equals(result.getStatus());
            Platform.runLater(() -> {
                populateFindings(findings);
                populateReports(results);
                populateOverview(result, results, findings);
                setScanControlsBusy(false);
                if (completed && !findings.isEmpty()) {
                    mainTabPane.getSelectionModel().select(1); // jump to Findings
                }
                showResultDialog(completed, findings.size(), str(mapGet(results, "engagement")));
            });
        } catch (Exception e) {
            Platform.runLater(() -> {
                setScanControlsBusy(false);
                showError("Could not load results", e.getMessage());
            });
        }
    }

    // ------------------------------------------------------------- rendering

    private void populateFindings(List<Finding> findings) {
        ObservableList<Finding> items = FXCollections.observableArrayList(findings);
        findingsListView.setItems(items);
        findingsListView.setPlaceholder(new Label(
            "No findings returned. If you expected results, confirm the Decepticon\n"
                + "KnowledgeGraph store is configured, then check the Logs tab."));
    }

    private void populateReports(Map<String, Object> results) {
        Object reportObj = mapGet(results, "report");
        if (!(reportObj instanceof Map)) {
            executiveSummaryTextArea.setText("No report available for this scan.");
            technicalReportTextArea.clear();
            remediationTextArea.clear();
            return;
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> report = (Map<String, Object>) reportObj;
        executiveSummaryTextArea.setText(str(report.get("executive_summary")));
        technicalReportTextArea.setText(str(report.get("technical_report")));
        remediationTextArea.setText(str(report.get("remediation_guide")));
    }

    private void populateOverview(ScanResult result, Map<String, Object> results, List<Finding> findings) {
        ovTarget.setText(orDash(result.getTarget()));
        ovEngagement.setText(orDash(str(mapGet(results, "engagement"))));
        ovAgent.setText(orDash(str(mapGet(results, "agent_role"))));
        ovScanMode.setText(orDash(str(mapGet(results, "scan_mode"))));
        ovStatus.setText(orDash(result.getStatus()));

        int critical = 0, high = 0, medium = 0, low = 0, info = 0;
        for (Finding finding : findings) {
            switch (finding.severity()) {
                case CRITICAL: critical++; break;
                case HIGH: high++; break;
                case MEDIUM: medium++; break;
                case LOW: low++; break;
                default: info++; break;
            }
        }
        countCritical.setText(Integer.toString(critical));
        countHigh.setText(Integer.toString(high));
        countMedium.setText(Integer.toString(medium));
        countLow.setText(Integer.toString(low));
        countInfo.setText(Integer.toString(info));
        countTotal.setText(Integer.toString(findings.size()));
    }

    private void resetResults() {
        findingsListView.setItems(FXCollections.observableArrayList());
        findingsListView.setPlaceholder(new Label("No findings yet — start an engagement."));
        executiveSummaryTextArea.clear();
        technicalReportTextArea.clear();
        remediationTextArea.clear();
        logTextArea.clear();
        for (Label label : List.of(countCritical, countHigh, countMedium, countLow, countInfo, countTotal)) {
            label.setText("0");
        }
        for (Label label : List.of(ovTarget, ovEngagement, ovAgent, ovScanMode, ovStatus)) {
            label.setText("—");
        }
    }

    // ------------------------------------------------------------ run status

    /**
     * Update the status dot + text. {@code kind} is the backend status
     * (idle/pending/running/completed/failed); {@code startedAt == 0} omits the
     * elapsed-time suffix.
     */
    private void setStatus(String kind, String detail, long startedAt) {
        statusDot.getStyleClass().setAll("status-dot", dotClass(kind));
        StringBuilder text = new StringBuilder(capitalize(kind));
        if (detail != null && !detail.isEmpty()) {
            text.append("  ·  ").append(detail);
        }
        if (startedAt > 0) {
            text.append("  ·  ").append(elapsed(startedAt));
        }
        statusLabel.setText(text.toString());
    }

    private void setBackendOnline(boolean online) {
        backendStatusLabel.getStyleClass().setAll("status-pill", online ? "pill-online" : "pill-offline");
        backendStatusLabel.setText(online ? "Backend: connected" : "Backend: offline");
    }

    private void setScanControlsBusy(boolean busy) {
        startScanButton.setDisable(busy);
        targetTextField.setDisable(busy);
        scopeComboBox.setDisable(busy);
        safeModeCheckBox.setDisable(busy);
        cancelButton.setDisable(!busy);
        if (!busy) {
            // Leave a completed bar full; only reset when returning to idle.
            if (progressBar.getProgress() == ProgressBar.INDETERMINATE_PROGRESS) {
                progressBar.setProgress(0);
            }
        }
    }

    // -------------------------------------------------------------- lifecycle

    private void startHealthChecks() {
        healthExecutor = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "backend-health");
            thread.setDaemon(true);
            return thread;
        });
        healthExecutor.scheduleWithFixedDelay(() -> {
            boolean online;
            try {
                online = apiService.healthCheck();
            } catch (Exception e) {
                online = false;
            }
            final boolean result = online;
            Platform.runLater(() -> setBackendOnline(result));
        }, 0, 5, TimeUnit.SECONDS);
    }

    /** Called from {@code Main.stop()} so the daemon pool doesn't outlive the UI. */
    public void shutdown() {
        if (currentTask != null) {
            currentTask.cancel();
        }
        if (healthExecutor != null) {
            healthExecutor.shutdownNow();
        }
    }

    // ----------------------------------------------------------------- helpers

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> extractList(ScanResult result, String key) {
        if (result == null || result.getResults() == null) {
            return Collections.emptyList();
        }
        Object value = result.getResults().get(key);
        if (value instanceof List) {
            return (List<Map<String, Object>>) value;
        }
        return Collections.emptyList();
    }

    private static Object mapGet(Map<String, Object> map, String key) {
        return map == null ? null : map.get(key);
    }

    private String mapScopeToBackend(String uiScope) {
        switch (uiScope) {
            case "Quick Scan": return "quick";
            case "Web Scan": return "web";
            case "Network Scan": return "network";
            case "API Scan": return "api";
            case "Full Scan":
            default: return "full";
        }
    }

    /** The Decepticon scan-mode a UI scope resolves to (for the overview panel). */
    private String mapScopeToMode(String uiScope) {
        switch (mapScopeToBackend(uiScope)) {
            case "quick": return "quick";
            case "full": return "deep";
            default: return "standard";
        }
    }

    private static String dotClass(String kind) {
        switch (kind) {
            case "running":
            case "pending":
                return "dot-running";
            case "completed":
                return "dot-ok";
            case "failed":
                return "dot-fail";
            default:
                return "dot-idle";
        }
    }

    private static String elapsed(long startedAt) {
        long seconds = Math.max(0, (System.currentTimeMillis() - startedAt) / 1000);
        return String.format("%02d:%02d", seconds / 60, seconds % 60);
    }

    private static String capitalize(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        return Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }

    private static String firstNonEmpty(String a, String b) {
        return a != null && !a.isEmpty() ? a : (b == null ? "" : b);
    }

    private static String str(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static String orDash(String value) {
        return value == null || value.isEmpty() ? "—" : value;
    }

    private void sleepQuietly() {
        try {
            Thread.sleep(POLL_INTERVAL_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void showResultDialog(boolean completed, int findingCount, String engagement) {
        if (completed) {
            showInfo("Engagement complete",
                findingCount + " finding(s) returned"
                    + (engagement.isEmpty() ? "." : " for " + engagement + "."));
        } else {
            showError("Engagement failed", "See the Logs tab and the Reports tab for details.");
        }
    }

    private void showInfo(String title, String message) {
        Dialogs.show(javafx.scene.control.Alert.AlertType.INFORMATION, title, message);
    }

    private void showWarning(String title, String message) {
        Dialogs.show(javafx.scene.control.Alert.AlertType.WARNING, title, message);
    }

    private void showError(String title, String message) {
        Dialogs.show(javafx.scene.control.Alert.AlertType.ERROR, title, message);
    }

    /** Small helper so alert plumbing doesn't clutter the controller body. */
    private static final class Dialogs {
        static void show(javafx.scene.control.Alert.AlertType type, String title, String message) {
            javafx.scene.control.Alert alert = new javafx.scene.control.Alert(type);
            alert.setTitle(title);
            alert.setHeaderText(null);
            alert.setContentText(message == null ? "" : message);
            alert.showAndWait();
        }
    }
}
