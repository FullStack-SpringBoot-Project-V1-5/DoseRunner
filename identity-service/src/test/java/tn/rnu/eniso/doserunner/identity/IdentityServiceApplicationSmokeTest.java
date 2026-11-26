package tn.rnu.eniso.doserunner.identity;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;

/** Boots the whole service over real HTTP and checks it is healthy and wired to commons (trace id echoed). */
@IdentityIntegrationTest
class IdentityServiceApplicationSmokeTest {

    @Value("${local.server.port}")
    int port;

    @Test
    void healthIsUpPublicAndTraceIdIsEchoed() throws Exception {
        HttpResponse<String> res = get("/actuator/health", "smoke-test-1");
        assertThat(res.statusCode()).isEqualTo(200);
        assertThat(res.body()).contains("\"status\":\"UP\"");
        assertThat(res.headers().firstValue("x-request-id")).contains("smoke-test-1");
    }

    @Test
    void protectedPathWithoutTokenGives401WithTraceIdInBody() throws Exception {
        HttpResponse<String> res = get("/does-not-exist", "smoke-test-2");
        assertThat(res.statusCode()).isEqualTo(401);
        assertThat(res.body()).contains("\"traceId\":\"smoke-test-2\"");
    }

    private HttpResponse<String> get(String path, String requestId) throws Exception {
        try (HttpClient client = HttpClient.newHttpClient()) {
            return client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                    .header("x-request-id", requestId).build(), HttpResponse.BodyHandlers.ofString());
        }
    }
}
