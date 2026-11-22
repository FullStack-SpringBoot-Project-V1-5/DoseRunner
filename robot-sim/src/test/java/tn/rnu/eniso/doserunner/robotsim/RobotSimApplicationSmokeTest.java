package tn.rnu.eniso.doserunner.robotsim;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import tn.rnu.eniso.doserunner.robotsim.facade.SimulationFleet;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;

/** Boots the whole service and checks it is healthy and wired to commons (trace id echoed). */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"sim.listener-enabled=false", "sim.scheduling-enabled=false", "management.health.jms.enabled=false",
                "management.endpoint.health.group.readiness.include=readinessState",
                "SIM_PHARMACY_X=5", "SIM_PHARMACY_Y=7"})
class RobotSimApplicationSmokeTest {

    @Value("${local.server.port}")
    int port;

    @Autowired
    SimulationFleet fleet;

    @Test
    void pharmacyEnvironmentPropertiesBindThroughApplicationConfiguration() {
        for (String id : java.util.List.of("R-01", "R-02", "R-03")) {
            assertThat(fleet.snapshot(id).x()).isEqualTo(5);
            assertThat(fleet.snapshot(id).y()).isEqualTo(7);
        }
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
