package org.factor_investing.quant_strategy.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.auth.oauth2.GoogleCredentials;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

@Service
public class CloudRunPriceJobLauncher {
    private final String jobName;
    private final ObjectMapper json;

    public CloudRunPriceJobLauncher(@Value("${price-data.import.cloud-run-job:}") String jobName, ObjectMapper json) {
        this.jobName = jobName;
        this.json = json;
    }

    public boolean enabled() {
        return !jobName.isBlank();
    }

    public void launch(PriceUpdateJobService.Job job) throws Exception {
        if (!jobName.matches("projects/[a-zA-Z0-9-]+/locations/[a-z0-9-]+/jobs/[a-z0-9-]+")) {
            throw new IllegalArgumentException("Invalid Cloud Run job resource name");
        }
        GoogleCredentials credentials = GoogleCredentials.getApplicationDefault()
                .createScoped("https://www.googleapis.com/auth/cloud-platform");
        credentials.refreshIfExpired();
        var env = List.of(
                Map.of("name", "PRICE_DATA_IMPORT_SOURCE", "value", job.source()),
                Map.of("name", "PRICE_DATA_IMPORT_TIME_FRAME", "value", job.timeFrame().name()),
                Map.of("name", "PRICE_DATA_IMPORT_RUN_ID", "value", job.id()));
        String body = json.writeValueAsString(Map.of("overrides", Map.of(
                "taskCount", 1, "containerOverrides", List.of(Map.of("env", env)))));
        var request = HttpRequest.newBuilder(URI.create("https://run.googleapis.com/v2/" + jobName + ":run"))
                .timeout(Duration.ofSeconds(30))
                .header("Authorization", "Bearer " + credentials.getAccessToken().getTokenValue())
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body)).build();
        try (var client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build()) {
            var response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("Cloud Run job dispatch returned HTTP " + response.statusCode());
            }
        }
    }
}
