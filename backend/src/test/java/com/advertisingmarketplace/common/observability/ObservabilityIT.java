package com.advertisingmarketplace.common.observability;

import static org.assertj.core.api.Assertions.assertThat;

import com.advertisingmarketplace.support.AbstractIntegrationTest;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.server.LocalManagementPort;
import org.springframework.test.context.TestPropertySource;

@TestPropertySource(properties = {
        "management.defaults.metrics.export.enabled=true",
        "management.prometheus.metrics.export.enabled=true",
        "management.tracing.enabled=true",
        "management.tracing.sampling.probability=1.0"
})
class ObservabilityIT extends AbstractIntegrationTest {

    private final HttpClient http = HttpClient.newHttpClient();

    @LocalManagementPort
    private int managementPort;

    @Test
    void prometheusIsServedOnTheManagementPortWithoutAuthentication() throws Exception {
        HttpResponse<String> response = get(managementPort, "/actuator/prometheus");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("http_server_requests_seconds", "jvm_memory_used_bytes",
                "marketplace_realtime_sse_connections");
    }

    @Test
    void actuatorIsNotReachableOnTheApplicationPort() throws Exception {
        assertThat(managementPort).isNotEqualTo(port);
        assertThat(get(port, "/actuator/prometheus").statusCode()).isIn(401, 404);
        assertThat(get(port, "/actuator/env").statusCode()).isIn(401, 404);
    }

    @Test
    void probesArePublicOnTheApplicationPort() throws Exception {
        HttpResponse<String> readiness = get(port, "/readyz");
        HttpResponse<String> liveness = get(port, "/livez");

        assertThat(readiness.statusCode()).isEqualTo(200);
        assertThat(readiness.body()).contains("UP");
        assertThat(liveness.statusCode()).isEqualTo(200);
    }

    @Test
    void responsesAndProblemDetailsCarryTheTraceId() throws Exception {
        HttpResponse<String> response = http.send(HttpRequest.newBuilder(uri(port, "/api/v1/auth/login"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("""
                        {"email":"nobody@marketplace.test","password":"wrong-passw0rd"}
                        """))
                .build(), HttpResponse.BodyHandlers.ofString());

        String traceId = response.headers().firstValue(TraceIds.HEADER).orElseThrow();
        assertThat(traceId).matches("[0-9a-f]{32}");
        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(response.body()).contains("\"traceId\":\"" + traceId + "\"");
    }

    @Test
    void unauthenticatedErrorsWrittenBySecurityCarryTheTraceId() throws Exception {
        HttpResponse<String> response = get(port, "/api/v1/deals");

        assertThat(response.statusCode()).isEqualTo(401);
        String traceId = response.headers().firstValue(TraceIds.HEADER).orElseThrow();
        assertThat(response.body()).contains("\"traceId\":\"" + traceId + "\"");
    }

    @Test
    void businessCountersAreExported() throws Exception {
        signup("Metric Marketer", "metrics@marketplace.test", "MARKETER");
        login("metrics@marketplace.test", PASSWORD);

        String metrics = get(managementPort, "/actuator/prometheus").body();

        assertThat(metrics).containsPattern("marketplace_auth_logins_total\\{[^}]*outcome=\"success\"[^}]*} [1-9]");
    }

    private HttpResponse<String> get(int targetPort, String path) throws IOException, InterruptedException {
        return http.send(HttpRequest.newBuilder(uri(targetPort, path)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private static URI uri(int targetPort, String path) {
        return URI.create("http://localhost:" + targetPort + path);
    }
}
