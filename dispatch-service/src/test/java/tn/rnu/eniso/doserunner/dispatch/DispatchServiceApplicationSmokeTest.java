package tn.rnu.eniso.doserunner.dispatch;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import tn.rnu.eniso.doserunner.dispatch.config.DispatchMessagingProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import tn.rnu.eniso.doserunner.testsupport.TestJwt;
import tn.rnu.eniso.doserunner.testsupport.TestJwtConfiguration;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;

/** Boots the whole service and checks it is healthy and wired to commons (trace id echoed, JWT security on). */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"DISPATCH_PHARMACY_X=5", "DISPATCH_PHARMACY_Y=7"})
@Import(TestJwtConfiguration.class)
class DispatchServiceApplicationSmokeTest extends PostgresTestSupport {

    @Value("${local.server.port}")
    int port;

    @Autowired
    DispatchMessagingProperties messaging;

    @Test
    void pharmacyEnvironmentPropertiesBindThroughApplicationConfiguration() {
        assertThat(messaging.pharmacyX()).isEqualTo(5);
        assertThat(messaging.pharmacyY()).isEqualTo(7);
    }

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
                    .header("Authorization", TestJwt.bearer("OPERATOR")).build(), HttpResponse.BodyHandlers.ofString());
        }
        assertThat(res.statusCode()).isEqualTo(404);
        assertThat(res.body()).contains("\"traceId\":\"smoke-test-2\"");
    }

    @Test
    void i2AnyNonPublicPathWithoutATokenIs401ApiError() throws Exception {
        HttpResponse<String> res = send("/fleet/robots", null);
        assertThat(res.statusCode()).isEqualTo(401);
        assertThat(res.headers().firstValue("WWW-Authenticate")).contains("Bearer");
        assertThat(res.body()).contains("\"status\":401", "\"traceId\":\"smoke-i2\"");
    }

    @Test
    void i2ExpiredTokenIs401AndFleetBoardStaysPublic() throws Exception {
        assertThat(send("/fleet/robots", TestJwt.expired("OPERATOR")).statusCode()).isEqualTo(401);
        assertThat(send("/ui/fleet", null).statusCode()).isEqualTo(200);
    }

    private HttpResponse<String> send(String path, String token) throws Exception {
        HttpRequest.Builder req = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("x-request-id", "smoke-i2");
        if (token != null) {
            req.header("Authorization", "Bearer " + token);
        }
        try (HttpClient client = HttpClient.newHttpClient()) {
            return client.send(req.build(), HttpResponse.BodyHandlers.ofString());
        }
    }
}
