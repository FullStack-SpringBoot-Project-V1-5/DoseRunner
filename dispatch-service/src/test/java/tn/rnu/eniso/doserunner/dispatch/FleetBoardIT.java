package tn.rnu.eniso.doserunner.dispatch;

import jakarta.jms.ConnectionFactory;
import jakarta.jms.DeliveryMode;
import jakarta.jms.Session;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tn.rnu.eniso.doserunner.contracts.messaging.Addresses;
import tn.rnu.eniso.doserunner.contracts.messaging.AvroJmsMessages;
import tn.rnu.eniso.doserunner.contracts.messaging.MessageHeaders;
import tn.rnu.eniso.doserunner.contracts.robot.RobotTelemetry;
import tn.rnu.eniso.doserunner.dispatch.facade.RobotTelemetryFacade;
import static org.assertj.core.api.Assertions.*;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "dispatch.messaging.relay-enabled=false")
@DirtiesContext
class FleetBoardIT {
    @Container static final PostgreSQLContainer DB = new PostgreSQLContainer("postgres:16.16");
    @Container static final GenericContainer<?> BROKER = ArtemisTestBroker.create();

    @DynamicPropertySource static void properties(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", DB::getJdbcUrl);
        r.add("spring.datasource.username", DB::getUsername);
        r.add("spring.datasource.password", DB::getPassword);
        r.add("spring.artemis.broker-url", () -> "tcp://" + BROKER.getHost() + ":" + BROKER.getMappedPort(61616));
        r.add("spring.artemis.user", () -> "c004");
        r.add("spring.artemis.password", () -> "c004-test-password");
    }

    @Value("${local.server.port}") int port;
    @Autowired ConnectionFactory factory;
    @Autowired AvroJmsMessages messages;
    @Autowired JdbcTemplate jdbc;
    @Autowired RobotTelemetryFacade telemetry;

    @BeforeEach void reset() {
        jdbc.update("DELETE FROM missions");
        jdbc.update("UPDATE robots SET x=0, y=0, battery=100, status='IDLE'");
    }

    HttpResponse<String> page() throws Exception {
        try (var client = HttpClient.newHttpClient()) {
            return client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/ui/fleet"))
                    .header("x-request-id", "fleet-test").build(), HttpResponse.BodyHandlers.ofString());
        }
    }

    static String robotRow(String html, String id) {
        var matcher = Pattern.compile("(?s)<tr data-robot-id=\"" + Pattern.quote(id) + "\">(.*?)</tr>").matcher(html);
        assertThat(matcher.find()).as("robot row %s", id).isTrue();
        return matcher.group(1);
    }

    @Test void rendersSeedsRefreshAndOnlyActiveMissions() throws Exception {
        var empty = page();
        assertThat(empty.statusCode()).isEqualTo(200);
        assertThat(empty.headers().firstValue("Content-Type")).hasValueSatisfying(v -> assertThat(v).startsWith("text/html"));
        assertThat(empty.headers().firstValue("Cache-Control")).contains("no-store");
        assertThat(empty.headers().firstValue("x-request-id")).contains("fleet-test");
        assertThat(empty.body()).contains("<meta http-equiv=\"refresh\" content=\"2\">", "No active missions.");
        for (String id : new String[]{"R-01", "R-02", "R-03"}) {
            assertThat(robotRow(empty.body(), id)).contains("100%", "IDLE");
        }
        UUID active = UUID.randomUUID(), order = UUID.randomUUID(), terminal = UUID.randomUUID();
        jdbc.update("INSERT INTO missions VALUES (?, ?, 'R-01', 'ASSIGNED')", active, order);
        jdbc.update("INSERT INTO missions VALUES (?, ?, 'R-02', 'COMPLETED')", terminal, UUID.randomUUID());
        jdbc.update("UPDATE robots SET status='BUSY' WHERE id='R-01'");
        String html = page().body();
        assertThat(html).contains(active.toString(), order.toString(), "ASSIGNED").doesNotContain(terminal.toString(), "No active missions.");
        assertThat(robotRow(html, "R-01")).contains("BUSY");
    }

    @Test void realAvroTelemetryAndDuplicateUpdatePersistAndRenderWithoutChangingAssignment() throws Exception {
        UUID mission = UUID.randomUUID();
        jdbc.update("INSERT INTO missions VALUES (?, ?, 'R-01', 'ASSIGNED')", mission, UUID.randomUUID());
        jdbc.update("UPDATE robots SET status='BUSY' WHERE id='R-01'");
        try (var connection = factory.createConnection(); var session = connection.createSession(Session.SESSION_TRANSACTED);
             var producer = session.createProducer(session.createTopic(Addresses.ROBOT_TELEMETRY))) {
            for (int x : new int[]{1, 1, 2}) {
                var message = messages.toMessage(session, new RobotTelemetry("R-01", x, 0, 100 - x));
                message.setStringProperty(MessageHeaders.ROBOT_ID, "R-01");
                message.setStringProperty(MessageHeaders.TRACE_ID, "fleet-telemetry");
                producer.send(message, DeliveryMode.PERSISTENT, 4, 0);
                session.commit();
                until(() -> jdbc.queryForObject("SELECT x FROM robots WHERE id='R-01'", Integer.class) == x);
                assertThat(robotRow(page().body(), "R-01")).contains("<td>" + x + "</td>", (100 - x) + "%", "BUSY");
            }
        }
        assertThat(jdbc.queryForObject("SELECT status FROM missions WHERE id=?", String.class, mission)).isEqualTo("ASSIGNED");
        assertThat(jdbc.queryForObject("SELECT x FROM robots WHERE id='R-02'", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT battery FROM robots WHERE id='R-02'", Integer.class)).isEqualTo(100);
    }

    @Test void rejectsUnknownRobotAndRollsBackDatabaseFailure() {
        assertThatThrownBy(() -> telemetry.update(new RobotTelemetry("unknown", 1, 0, 99)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Unknown");
        jdbc.execute("CREATE FUNCTION reject_telemetry_commit() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'synthetic telemetry failure'; END $$");
        jdbc.execute("CREATE CONSTRAINT TRIGGER reject_telemetry_commit AFTER UPDATE ON robots DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION reject_telemetry_commit()");
        try {
            assertThatThrownBy(() -> telemetry.update(new RobotTelemetry("R-01", 1, 0, 99)))
                    .isInstanceOf(RuntimeException.class).hasStackTraceContaining("synthetic telemetry failure");
            assertThat(jdbc.queryForObject("SELECT x FROM robots WHERE id='R-01'", Integer.class)).isZero();
            assertThat(jdbc.queryForObject("SELECT battery FROM robots WHERE id='R-01'", Integer.class)).isEqualTo(100);
        } finally {
            jdbc.execute("DROP TRIGGER reject_telemetry_commit ON robots");
            jdbc.execute("DROP FUNCTION reject_telemetry_commit()");
        }
        telemetry.update(new RobotTelemetry("R-01", 0, 0, 0));
        assertThat(jdbc.queryForObject("SELECT battery FROM robots WHERE id='R-01'", Integer.class)).isZero();
    }

    @Test void escapesRobotIdentifiersInRenderedHtml() throws Exception {
        String unsafe = "<script>alert(1)</script>";
        jdbc.update("UPDATE robots SET id=? WHERE id='R-03'", unsafe);
        try {
            assertThat(page().body()).contains("&lt;script&gt;alert(1)&lt;/script&gt;").doesNotContain(unsafe);
        } finally {
            jdbc.update("UPDATE robots SET id='R-03' WHERE id=?", unsafe);
        }
    }

    @Test void invalidBrokerTelemetryReachesDlqWithoutChangingFleet() throws Exception {
        try (var connection = factory.createConnection(); var session = connection.createSession();
             var producer = session.createProducer(session.createTopic(Addresses.ROBOT_TELEMETRY))) {
            connection.start();
            for (int kind = 0; kind < 4; kind++) {
                String probe = UUID.randomUUID().toString();
                try (var dlq = session.createConsumer(session.createQueue(Addresses.DLQ), "probeId='" + probe + "'")) {
                    var message = kind == 0 ? session.createTextMessage("invalid telemetry")
                            : messages.toMessage(session, new RobotTelemetry(kind == 3 ? "unknown" : "R-01", 1, 0, kind == 1 ? 101 : 99));
                    message.setStringProperty("probeId", probe);
                    if (kind == 2) message.setStringProperty(MessageHeaders.ROBOT_ID, "R-02");
                    producer.send(message);
                    var dead = dlq.receive(15000);
                    assertThat(dead).as("poison kind %s", kind).isNotNull();
                    assertThat(dead.getStringProperty("_AMQ_ORIG_QUEUE")).isEqualTo(Addresses.QUEUE_DISPATCH_TELEMETRY);
                    assertThat(dlq.receive(200)).isNull();
                }
                assertThat(jdbc.queryForObject("SELECT sum(x) FROM robots", Integer.class)).isZero();
                assertThat(jdbc.queryForObject("SELECT sum(battery) FROM robots", Integer.class)).isEqualTo(300);
            }
        }
    }

    static void until(BooleanSupplier condition) throws Exception {
        long deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
        while (!condition.getAsBoolean() && System.nanoTime() < deadline) Thread.sleep(25);
        assertThat(condition.getAsBoolean()).isTrue();
    }
}
