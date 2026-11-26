package tn.rnu.eniso.doserunner.custody;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import tn.rnu.eniso.doserunner.custody.bo.LinkType;
import tn.rnu.eniso.doserunner.custody.facade.CustodyFacade;
import tn.rnu.eniso.doserunner.custody.facade.RecordCommand;
import tn.rnu.eniso.doserunner.testsupport.TestJwt;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Boots the whole service (Postgres + libsodium) and checks health, the REST API and the audit page over HTTP. */
@CustodyServiceTest
class CustodyServiceApplicationSmokeTest {

    @Value("${local.server.port}")
    int port;

    @Autowired
    CustodyFacade facade;

    @Autowired
    JdbcTemplate jdbc;

    /** GET as an AUDITOR (the role that can read everything here). */
    private HttpResponse<String> get(String path, String requestId) throws Exception {
        return get(path, requestId, TestJwt.forRoles("AUDITOR"));
    }

    /** GET with a given token, or none when {@code token} is null. */
    private HttpResponse<String> get(String path, String requestId, String token) throws Exception {
        HttpRequest.Builder req = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("x-request-id", requestId);
        if (token != null) {
            req.header("Authorization", "Bearer " + token);
        }
        try (HttpClient client = HttpClient.newHttpClient()) {
            return client.send(req.build(), HttpResponse.BodyHandlers.ofString());
        }
    }

    private String recordTwoLinks() {
        String order = "ORD-" + UUID.randomUUID();
        Instant t = Instant.parse("2026-11-20T08:00:00Z");
        facade.record(new RecordCommand(UUID.randomUUID().toString(), order, LinkType.ORDER_PLACED,
                "nurse-07", null, new byte[]{1}, t));
        facade.record(new RecordCommand(UUID.randomUUID().toString(), order, LinkType.PICKUP_CONFIRMED,
                "pharm-02", "R-01", new byte[]{2}, t.plusSeconds(60)));
        return order;
    }

    @Test
    void healthIsUpAndTraceIdIsEchoed() throws Exception {
        HttpResponse<String> res = get("/actuator/health", "smoke-test-1");

        assertThat(res.statusCode()).isEqualTo(200);
        assertThat(res.body()).contains("\"status\":\"UP\"");
        assertThat(res.headers().firstValue("x-request-id")).contains("smoke-test-1");
    }

    @Test
    void unknownPathGives404WithTraceIdInBody() throws Exception {
        HttpResponse<String> res = get("/does-not-exist", "smoke-test-2");

        assertThat(res.statusCode()).isEqualTo(404);
        assertThat(res.body()).contains("\"traceId\":\"smoke-test-2\"");
    }

    @Test
    void verifyEndpointReturnsTheReport() throws Exception {
        String order = recordTwoLinks();

        HttpResponse<String> res = get("/custody/orders/" + order + "/verify", "smoke-test-3");

        assertThat(res.statusCode()).isEqualTo(200);
        assertThat(res.body()).contains("\"valid\":true", "\"links\":2").doesNotContain("brokenAtSeq");
    }

    @Test
    void chainEndpointListsLinksAndUnknownOrderIs404WithApiError() throws Exception {
        String order = recordTwoLinks();

        HttpResponse<String> chain = get("/custody/orders/" + order, "smoke-test-4");
        HttpResponse<String> missing = get("/custody/orders/ORD-nope/verify", "smoke-test-5");

        assertThat(chain.statusCode()).isEqualTo(200);
        assertThat(chain.body()).contains("\"seq\":1", "\"seq\":2", "\"type\":\"PICKUP_CONFIRMED\"");
        assertThat(missing.statusCode()).isEqualTo(404);
        assertThat(missing.body()).contains("\"traceId\":\"smoke-test-5\"", "no custody record for order ORD-nope");
    }

    @Test
    void publicKeyIs32BytesOfBase64() throws Exception {
        HttpResponse<String> res = get("/custody/public-key", "smoke-test-6");

        assertThat(res.statusCode()).isEqualTo(200);
        String key = res.body().replaceAll(".*\"publicKey\":\"([^\"]+)\".*", "$1");
        assertThat(Base64.getDecoder().decode(key)).hasSize(32);
    }

    @Test
    void auditPageShowsValidThenInvalidAfterTampering() throws Exception {
        String order = recordTwoLinks();

        HttpResponse<String> before = get("/ui/audit/" + order, "smoke-test-7");
        assertThat(before.statusCode()).isEqualTo(200);
        assertThat(before.body()).contains("badge valid", "VALID", "PICKUP_CONFIRMED").doesNotContain("INVALID");

        jdbc.execute("ALTER TABLE custody_event DISABLE TRIGGER custody_event_no_update_delete");
        try {
            jdbc.update("UPDATE custody_event SET robot_id = 'R-66' WHERE order_id = ? AND seq = 2", order);
        } finally {
            jdbc.execute("ALTER TABLE custody_event ENABLE TRIGGER custody_event_no_update_delete");
        }

        HttpResponse<String> after = get("/ui/audit/" + order, "smoke-test-8");
        assertThat(after.body()).contains("badge invalid", "INVALID", "chain broken at link <strong>2</strong>");
    }

    @Test
    void auditSearchRedirectsToTheOrderPage() throws Exception {
        HttpResponse<String> res = get("/ui/audit?orderId=ORD-42", "smoke-test-9");

        assertThat(res.statusCode()).isEqualTo(302);
        assertThat(res.headers().firstValue("location")).hasValueSatisfying(l -> assertThat(l).endsWith("/ui/audit/ORD-42"));
    }

    @Test
    void i2NoTokenIs401ApiErrorAndExpiredTokenIs401() throws Exception {
        HttpResponse<String> none = get("/custody/public-key", "smoke-i2-1", null);
        assertThat(none.statusCode()).isEqualTo(401);
        assertThat(none.headers().firstValue("WWW-Authenticate")).contains("Bearer");
        assertThat(none.body()).contains("\"status\":401", "\"traceId\":\"smoke-i2-1\"");
        assertThat(get("/custody/public-key", "smoke-i2-2", TestJwt.expired("AUDITOR")).statusCode()).isEqualTo(401);
    }

    @Test
    void i2PharmacistReadsTheChainButOnlyAuditorsVerify() throws Exception {
        String order = recordTwoLinks();
        String pharmacist = TestJwt.forRoles("PHARMACIST");

        assertThat(get("/custody/orders/" + order, "smoke-i2-3", pharmacist).statusCode()).isEqualTo(200);
        HttpResponse<String> verify = get("/custody/orders/" + order + "/verify", "smoke-i2-4", pharmacist);
        assertThat(verify.statusCode()).isEqualTo(403);
        assertThat(verify.body()).contains("\"status\":403", "\"traceId\":\"smoke-i2-4\"");
        assertThat(get("/custody/public-key", "smoke-i2-5", pharmacist).statusCode()).isEqualTo(403);
    }

    @Test
    void i2NurseCannotReadTheLedgerButTheAuditPageStaysPublic() throws Exception {
        String order = recordTwoLinks();
        assertThat(get("/custody/orders/" + order, "smoke-i2-6", TestJwt.forRoles("NURSE")).statusCode())
                .isEqualTo(403);
        assertThat(get("/ui/audit/" + order, "smoke-i2-7", null).statusCode()).isEqualTo(200);
    }
}
