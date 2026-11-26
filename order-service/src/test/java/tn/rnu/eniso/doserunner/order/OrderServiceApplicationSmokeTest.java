package tn.rnu.eniso.doserunner.order;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import tn.rnu.eniso.doserunner.testsupport.TestJwt;
import tn.rnu.eniso.doserunner.order.support.ContainersSupport;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class OrderServiceApplicationSmokeTest extends ContainersSupport {

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
    void unknownPathWithATokenGives404WithTraceIdInBody() throws Exception {
        HttpResponse<String> res;
        try (HttpClient client = HttpClient.newHttpClient()) {
            res = client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/does-not-exist"))
                    .header("x-request-id", "smoke-test-2")
                    .header("Authorization", TestJwt.bearer("NURSE")).build(), HttpResponse.BodyHandlers.ofString());
        }
        assertThat(res.statusCode()).isEqualTo(404);
        assertThat(res.body()).contains("\"traceId\":\"smoke-test-2\"");
    }
}
