package tn.rnu.eniso.doserunner.dispatch;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;

/** Boots the whole service and checks it is healthy and wired to commons (trace id echoed). */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class DispatchServiceApplicationSmokeTest {

    @Value("${local.server.port}")
    int port;

    @Test
    void healthIsUpAndTraceIdIsEchoed() throws Exception {
        HttpResponse<String> res;
        try (HttpClient client = HttpClient.newHttpClient()) {
            res = client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/actuator/health"))
                    .header("x-request-id", "smoke-test-1").build(), HttpResponse.BodyHandlers.ofString());
        }
        assertThat(res.statusCode()).isEqualTo(200);
        assertThat(res.body()).contains("\"status\":\"UP\"");
        assertThat(res.headers().firstValue("x-request-id")).contains("smoke-test-1");
    }

    @Test
    void unknownPathGives404WithTraceIdInBody() throws Exception {
        HttpResponse<String> res;
        try (HttpClient client = HttpClient.newHttpClient()) {
            res = client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/does-not-exist"))
                    .header("x-request-id", "smoke-test-2").build(), HttpResponse.BodyHandlers.ofString());
        }
        assertThat(res.statusCode()).isEqualTo(404);
        assertThat(res.body()).contains("\"traceId\":\"smoke-test-2\"");
    }
}
