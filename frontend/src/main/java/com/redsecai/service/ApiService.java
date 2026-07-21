package com.redsecai.service;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.redsecai.model.ScanRequest;
import com.redsecai.model.ScanResponse;
import com.redsecai.model.ScanResult;
import com.redsecai.model.ScanStatus;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Thin HTTP client for the OffensiveRed backend.
 */
public class ApiService {
    private final String baseUrl;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public ApiService(String baseUrl) {
        this.baseUrl = baseUrl;
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
        this.objectMapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    /** POST /scan/start */
    public ScanResponse startScan(ScanRequest request) throws IOException, InterruptedException {
        String jsonBody = objectMapper.writeValueAsString(request);
        HttpRequest httpRequest = HttpRequest.newBuilder()
            .uri(URI.create(baseUrl + "/scan/start"))
            .timeout(Duration.ofSeconds(30))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
            .build();
        HttpResponse<String> response = send(httpRequest);
        if (response.statusCode() == 200) {
            return objectMapper.readValue(response.body(), ScanResponse.class);
        }
        throw new IOException("Failed to start scan (HTTP " + response.statusCode() + "): "
            + response.body());
    }

    /** GET /scan/status/{id} — live phase, progress, and recent log lines. */
    public ScanStatus getScanStatus(String scanId) throws IOException, InterruptedException {
        HttpResponse<String> response = send(get("/scan/status/" + scanId));
        if (response.statusCode() == 200) {
            return objectMapper.readValue(response.body(), ScanStatus.class);
        }
        throw new IOException("Failed to get scan status (HTTP " + response.statusCode() + ")");
    }

    /** GET /scan/result/{id} — findings and report. */
    public ScanResult getScanResult(String scanId) throws IOException, InterruptedException {
        HttpResponse<String> response = send(get("/scan/result/" + scanId));
        if (response.statusCode() == 200) {
            return objectMapper.readValue(response.body(), ScanResult.class);
        }
        throw new IOException("Failed to get scan result (HTTP " + response.statusCode() + ")");
    }

    /** GET /health — true when the backend is reachable. */
    public boolean healthCheck() throws IOException, InterruptedException {
        HttpResponse<String> response = send(get("/health"));
        return response.statusCode() == 200;
    }

    private HttpRequest get(String path) {
        return HttpRequest.newBuilder()
            .uri(URI.create(baseUrl + path))
            .timeout(Duration.ofSeconds(15))
            .GET()
            .build();
    }

    private HttpResponse<String> send(HttpRequest request) throws IOException, InterruptedException {
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }
}
