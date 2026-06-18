package com.redsecai.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.redsecai.model.ScanRequest;
import com.redsecai.model.ScanResponse;
import com.redsecai.model.ScanResult;
import com.redsecai.service.ApiService;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;

import java.net.URL;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;

/**
 * Main controller for the RedSecAI GUI
 */
public class MainController implements Initializable {
    
    @FXML private TextField targetTextField;
    @FXML private ComboBox<String> scopeComboBox;
    @FXML private CheckBox safeModeCheckBox;
    @FXML private Button startScanButton;
    @FXML private Button cancelButton;
    @FXML private ProgressBar progressBar;
    @FXML private Text statusText;
    @FXML private TextArea logTextArea;
    @FXML private TabPane mainTabPane;
    @FXML private Tab findingsTab;
    @FXML private Tab attackPathsTab;
    @FXML private Tab reportTab;
    @FXML private ListView<String> findingsListView;
    @FXML private ListView<String> attackPathsListView;
    @FXML private TextArea executiveSummaryTextArea;
    @FXML private TextArea technicalReportTextArea;
    @FXML private TextArea remediationTextArea;
    
    private ApiService apiService;
    private ObjectMapper objectMapper;
    private String currentScanId;
    private Task<Void> currentTask;
    
    @Override
    public void initialize(URL location, ResourceBundle resources) {
        apiService = new ApiService("http://localhost:8000");
        objectMapper = new ObjectMapper();
        
        // Initialize scope options
        scopeComboBox.setItems(FXCollections.observableArrayList(
            "Full Scan", "Quick Scan", "Web Scan", "Network Scan", "API Scan"
        ));
        scopeComboBox.getSelectionModel().selectFirst();
        
        // Set default safe mode
        safeModeCheckBox.setSelected(true);
        
        // Initialize UI state
        setScanInProgress(false);
        statusText.setText("Ready to scan");
        logTextArea.setEditable(false);
        
        // Setup event handlers
        startScanButton.setOnAction(e -> startScan());
        cancelButton.setOnAction(e -> cancelScan());
        
        // Setup tab selection handlers
        findingsTab.setOnSelectionChanged(e -> {
            if (findingsTab.isSelected()) {
                loadFindings();
            }
        });
        
        attackPathsTab.setOnSelectionChanged(e -> {
            if (attackPathsTab.isSelected()) {
                loadAttackPaths();
            }
        });
        
        reportTab.setOnSelectionChanged(e -> {
            if (reportTab.isSelected()) {
                loadReports();
            }
        });
    }
    
    @FXML
    private void startScan() {
        String target = targetTextField.getText().trim();
        if (target.isEmpty()) {
            showAlert("Error", "Please enter a target URL or domain");
            return;
        }
        
        String scope = scopeComboBox.getSelectionModel().getSelectedItem();
        boolean safeMode = safeModeCheckBox.isSelected();
        
        setScanInProgress(true);
        logMessage("Starting scan for target: " + target);
        logMessage("Scope: " + scope);
        logMessage("Safe Mode: " + (safeMode ? "Enabled" : "Disabled"));
        
        currentTask = new Task<Void>() {
            @Override
            protected Void call() throws Exception {
                try {
                    // Create scan request
                    ScanRequest request = new ScanRequest();
                    request.setTarget(target);
                    request.setScope(List.of(mapScopeToBackend(scope)));
                    request.setSafeMode(safeMode);
                    
                    // Start scan
                    ScanResponse response = apiService.startScan(request);
                    currentScanId = response.getScanId();
                    
                    logMessage("Scan started with ID: " + currentScanId);
                    
                    // Poll for results
                    pollScanStatus();
                    
                } catch (Exception e) {
                    logMessage("Error: " + e.getMessage());
                    Platform.runLater(() -> {
                        showAlert("Error", "Failed to start scan: " + e.getMessage());
                        setScanInProgress(false);
                    });
                }
                return null;
            }
        };
        
        new Thread(currentTask).start();
    }
    
    @FXML
    private void cancelScan() {
        if (currentTask != null && currentTask.isRunning()) {
            currentTask.cancel();
            logMessage("Scan cancelled by user");
            setScanInProgress(false);
        }
    }
    
    private void pollScanStatus() throws Exception {
        int maxAttempts = 120; // 2 minutes with 1-second intervals
        int attempts = 0;
        
        while (attempts < maxAttempts && !currentTask.isCancelled()) {
            ScanResult result = apiService.getScanResult(currentScanId);
            
            if ("completed".equals(result.getStatus())) {
                logMessage("Scan completed successfully!");
                Platform.runLater(() -> {
                    statusText.setText("Scan completed");
                    progressBar.setProgress(1.0);
                    setScanInProgress(false);
                    refreshAllTabs();
                    showAlert("Success", "Scan completed successfully!");
                });
                return;
            } else if ("failed".equals(result.getStatus())) {
                logMessage("Scan failed");
                Platform.runLater(() -> {
                    statusText.setText("Scan failed");
                    progressBar.setProgress(0);
                    setScanInProgress(false);
                    refreshAllTabs();
                    showAlert("Error", "Scan failed. Check the Logs tab and Reports for details.");
                });
                return;
            }
            
            // Update progress
            double progress = (double) attempts / maxAttempts;
            Platform.runLater(() -> {
                progressBar.setProgress(progress);
                statusText.setText("Scanning... (" + (attempts + 1) + "/" + maxAttempts + ")");
            });
            
            attempts++;
            Thread.sleep(1000);
        }
        
        if (attempts >= maxAttempts) {
            logMessage("Scan timed out");
            Platform.runLater(() -> {
                statusText.setText("Scan timed out");
                setScanInProgress(false);
                showAlert("Error", "Scan timed out after 2 minutes");
            });
        }
    }
    
    private void refreshAllTabs() {
        loadFindings();
        loadAttackPaths();
        loadReports();
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> extractList(ScanResult result, String key) {
        if (result == null || result.getResults() == null) {
            return java.util.Collections.emptyList();
        }
        Object value = result.getResults().get(key);
        if (value instanceof List) {
            return (List<Map<String, Object>>) value;
        }
        return java.util.Collections.emptyList();
    }

    private static String str(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private void loadFindings() {
        if (currentScanId == null) {
            return;
        }
        try {
            ScanResult result = apiService.getScanResult(currentScanId);
            ObservableList<String> items = FXCollections.observableArrayList();
            for (Map<String, Object> finding : extractList(result, "findings")) {
                items.add(formatFinding(finding));
            }
            if (items.isEmpty()) {
                items.add("No findings (scan status: " + str(result.getStatus()) + ").");
            }
            findingsListView.setItems(items);
            logMessage("Loaded " + items.size() + " finding entry(ies)");
        } catch (Exception e) {
            logMessage("Error loading findings: " + e.getMessage());
        }
    }

    private String formatFinding(Map<String, Object> finding) {
        String severity = str(finding.get("severity"));
        String title = str(finding.get("title"));
        String technique = str(finding.get("technique_id"));
        String description = str(finding.get("description"));
        StringBuilder sb = new StringBuilder();
        sb.append("[").append(severity.isEmpty() ? "?" : severity).append("] ").append(title);
        if (!technique.isEmpty()) {
            sb.append("  (").append(technique).append(")");
        }
        if (!description.isEmpty()) {
            sb.append("\n    ").append(description);
        }
        return sb.toString();
    }

    private void loadAttackPaths() {
        if (currentScanId == null) {
            return;
        }
        try {
            ScanResult result = apiService.getScanResult(currentScanId);
            ObservableList<String> items = FXCollections.observableArrayList();
            for (Map<String, Object> path : extractList(result, "attack_paths")) {
                items.add(formatAttackPath(path));
            }
            if (items.isEmpty()) {
                items.add("No simulated attack paths for this scan.");
            }
            attackPathsListView.setItems(items);
            logMessage("Loaded " + items.size() + " attack-path entry(ies)");
        } catch (Exception e) {
            logMessage("Error loading attack paths: " + e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private String formatAttackPath(Map<String, Object> path) {
        StringBuilder sb = new StringBuilder();
        sb.append(str(path.get("name")));
        sb.append("  [likelihood: ").append(str(path.get("likelihood")))
          .append(", impact: ").append(str(path.get("impact"))).append("]");
        Object steps = path.get("steps");
        if (steps instanceof List) {
            int i = 1;
            for (Object step : (List<Object>) steps) {
                if (step instanceof Map) {
                    sb.append("\n    Step ").append(i++).append(": ")
                      .append(str(((Map<String, Object>) step).get("description")));
                }
            }
        }
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    private void loadReports() {
        if (currentScanId == null) {
            return;
        }
        try {
            ScanResult result = apiService.getScanResult(currentScanId);
            Map<String, Object> results = result.getResults();
            Object reportObj = results == null ? null : results.get("report");
            if (!(reportObj instanceof Map)) {
                executiveSummaryTextArea.setText(
                    "No report available yet (scan status: " + str(result.getStatus()) + ").");
                technicalReportTextArea.clear();
                remediationTextArea.clear();
                return;
            }
            Map<String, Object> report = (Map<String, Object>) reportObj;
            executiveSummaryTextArea.setText(str(report.get("executive_summary")));
            technicalReportTextArea.setText(str(report.get("technical_report")));
            remediationTextArea.setText(str(report.get("remediation_guide")));
            logMessage("Loaded reports");
        } catch (Exception e) {
            logMessage("Error loading reports: " + e.getMessage());
        }
    }
    
    private String mapScopeToBackend(String uiScope) {
        switch (uiScope) {
            case "Full Scan": return "full";
            case "Quick Scan": return "quick";
            case "Web Scan": return "web";
            case "Network Scan": return "network";
            case "API Scan": return "api";
            default: return "full";
        }
    }
    
    private void setScanInProgress(boolean inProgress) {
        startScanButton.setDisable(inProgress);
        targetTextField.setDisable(inProgress);
        scopeComboBox.setDisable(inProgress);
        safeModeCheckBox.setDisable(inProgress);
        cancelButton.setDisable(!inProgress);
    }
    
    private void logMessage(String message) {
        Platform.runLater(() -> {
            logTextArea.appendText(message + "\n");
        });
    }
    
    private void showAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
