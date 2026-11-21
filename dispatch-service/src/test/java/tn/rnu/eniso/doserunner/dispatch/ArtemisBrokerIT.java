package tn.rnu.eniso.doserunner.dispatch;

import jakarta.jms.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.UUID;
import org.apache.activemq.artemis.api.jms.management.JMSManagementHelper;
import org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tn.rnu.eniso.doserunner.contracts.messaging.Addresses;

import static org.assertj.core.api.Assertions.*;

@Testcontainers
class ArtemisBrokerIT {
    @Container
    static final GenericContainer<?> BROKER = ArtemisTestBroker.create();

    private ActiveMQConnectionFactory factory() {
        return new ActiveMQConnectionFactory("tcp://" + BROKER.getHost() + ":" + BROKER.getMappedPort(61616)
                + "?consumerWindowSize=0");
    }

    @Test
    void loadsProjectConfigurationAndFrozenBindings() throws Exception {
        String loaded = BROKER.copyFileFromContainer("/var/lib/artemis-instance/etc/broker.xml", input ->
                new String(input.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8));
        assertThat(loaded).isEqualTo(Files.readString(Path.of(System.getProperty("broker.configuration"))));
        try (var factory = factory(); var connection = factory.createConnection("c004", "c004-test-password")) {
            connection.start();
            try (var session = connection.createSession()) {
                for (String queue : new String[]{Addresses.QUEUE_ORDER_SERVICE, Addresses.QUEUE_DISPATCH_SERVICE,
                        Addresses.QUEUE_CUSTODY_SERVICE}) {
                    assertBinding(session, queue, Addresses.EVENTS, "MULTICAST");
                }
                assertThat(management(session, "queue." + Addresses.QUEUE_DISPATCH_SERVICE, "filter"))
                        .isEqualTo("eventType = 'OrderValidated'");
                assertBinding(session, Addresses.QUEUE_DISPATCH_TELEMETRY, Addresses.ROBOT_TELEMETRY, "MULTICAST");
                assertBinding(session, Addresses.ROBOT_COMMANDS, Addresses.ROBOT_COMMANDS, "ANYCAST");
                assertBinding(session, Addresses.DLQ, Addresses.DLQ, "ANYCAST");
                assertBinding(session, Addresses.EXPIRY_QUEUE, Addresses.EXPIRY_QUEUE, "ANYCAST");
                for (String address : new String[]{Addresses.EVENTS, Addresses.ROBOT_COMMANDS, Addresses.ROBOT_TELEMETRY}) {
                    String settings = management(session, "broker", "getAddressSettingsAsJSON", address).toString();
                    assertThat(settings).containsPattern("\"maxDeliveryAttempts\"\\s*:\\s*3")
                            .containsPattern("\"redeliveryDelay\"\\s*:\\s*1000")
                            .containsPattern("\"redeliveryMultiplier\"\\s*:\\s*2")
                            .contains("DLQ", "ExpiryQueue");
                }
            }
        }
        try (var factory = factory()) {
            assertThatThrownBy(() -> factory.createConnection("c004", "wrong-password"))
                    .isInstanceOf(JMSException.class);
        }
    }

    private void assertBinding(Session session, String queue, String address, String routing) throws Exception {
        assertThat(management(session, "queue." + queue, "address")).isEqualTo(address);
        assertThat(management(session, "queue." + queue, "routingType")).isEqualTo(routing);
        assertThat(management(session, "queue." + queue, "durable")).isEqualTo(true);
        Object[] types = (Object[]) management(session, "address." + address, "routingTypes");
        assertThat(types).containsExactly(routing);
    }

    private Object management(Session session, String resource, String name, Object... arguments) throws Exception {
        TemporaryQueue replies = session.createTemporaryQueue();
        try (var consumer = session.createConsumer(replies);
             var producer = session.createProducer(session.createQueue("activemq.management"))) {
            Message request = session.createMessage();
            if (arguments.length == 0) JMSManagementHelper.putAttribute(request, resource, name);
            else JMSManagementHelper.putOperationInvocation(request, resource, name, arguments);
            request.setJMSReplyTo(replies);
            producer.send(request);
            Message response = consumer.receive(5000);
            assertThat(response).as(resource + ":" + name).isNotNull();
            assertThat(JMSManagementHelper.hasOperationSucceeded(response)).isTrue();
            return JMSManagementHelper.getResult(response);
        } finally {
            replies.delete();
        }
    }

    @Test
    void deliversNormalMessageAndDeadLettersPoisonAfterExactlyThreeRollbacks() throws Exception {
        String probe = UUID.randomUUID().toString();
        try (var factory = factory(); var connection = factory.createConnection("c004", "c004-test-password")) {
            connection.start();
            try (var session = connection.createSession(true, Session.SESSION_TRANSACTED);
                 var deadLetters = connection.createSession();
                 var producer = session.createProducer(session.createQueue(Addresses.ROBOT_COMMANDS));
                 var consumer = session.createConsumer(session.createQueue(Addresses.ROBOT_COMMANDS), "probeId='" + probe + "'");
                 var dlq = deadLetters.createConsumer(deadLetters.createQueue(Addresses.DLQ), "probeId='" + probe + "'")) {
                BytesMessage normal = session.createBytesMessage();
                normal.writeUTF("synthetic C004 normal message");
                normal.setStringProperty("probeId", probe);
                producer.send(normal);
                session.commit();
                BytesMessage delivered = (BytesMessage) consumer.receive(5000);
                assertThat(delivered).isNotNull();
                assertThat(delivered.readUTF()).isEqualTo("synthetic C004 normal message");
                session.commit();
                BytesMessage poison = session.createBytesMessage();
                poison.writeUTF("synthetic C004 poison message");
                poison.setStringProperty("probeId", probe);
                producer.send(poison);
                session.commit();
                long rolledBackAt = 0;
                for (int attempt = 1; attempt <= 3; attempt++) {
                    Message received = consumer.receive(8000);
                    assertThat(received).as("delivery " + attempt).isNotNull();
                    assertThat(received.getIntProperty("JMSXDeliveryCount")).isEqualTo(attempt);
                    assertThat(received.getJMSRedelivered()).isEqualTo(attempt > 1);
                    if (attempt > 1) assertThat(System.nanoTime() - rolledBackAt)
                            .isGreaterThanOrEqualTo(Duration.ofMillis(attempt == 2 ? 800 : 1600).toNanos());
                    assertThat(dlq.receiveNoWait()).as("DLQ must be empty before rollback " + attempt).isNull();
                    session.rollback();
                    rolledBackAt = System.nanoTime();
                    System.out.println("C004 poison delivery=" + attempt + " rolled back");
                }
                BytesMessage dead = (BytesMessage) dlq.receive(5000);
                assertThat(dead).isNotNull();
                assertThat(dead.readUTF()).isEqualTo("synthetic C004 poison message");
                assertThat(dead.getStringProperty("_AMQ_ORIG_ADDRESS")).isEqualTo(Addresses.ROBOT_COMMANDS);
                assertThat(dead.getStringProperty("_AMQ_ORIG_QUEUE")).isEqualTo(Addresses.ROBOT_COMMANDS);
                assertThat(consumer.receive(2500)).as("no fourth delivery").isNull();
                assertThat(dlq.receive(500)).as("only one dead letter").isNull();
                System.out.println("C004 DLQ received after exactly 3 failures; no fourth source delivery");
            }
        }
    }
}
