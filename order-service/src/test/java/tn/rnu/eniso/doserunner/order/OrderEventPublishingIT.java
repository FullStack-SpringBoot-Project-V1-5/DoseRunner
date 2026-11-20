package tn.rnu.eniso.doserunner.order;

import com.jayway.jsonpath.JsonPath;
import jakarta.jms.Connection;
import jakarta.jms.ConnectionFactory;
import jakarta.jms.Message;
import jakarta.jms.MessageConsumer;
import jakarta.jms.Session;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import tn.rnu.eniso.doserunner.contracts.events.OrderPlaced;
import tn.rnu.eniso.doserunner.contracts.events.OrderValidated;
import tn.rnu.eniso.doserunner.contracts.messaging.Addresses;
import tn.rnu.eniso.doserunner.contracts.messaging.AvroJmsMessages;
import tn.rnu.eniso.doserunner.contracts.messaging.MessageHeaders;
import tn.rnu.eniso.doserunner.order.support.ContainersSupport;
import tn.rnu.eniso.doserunner.order.support.TestStock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class OrderEventPublishingIT extends ContainersSupport {

    private static final long RECEIVE_TIMEOUT_MS = 10_000;

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ConnectionFactory connectionFactory;

    @Autowired
    private AvroJmsMessages messages;

    @Autowired
    private JdbcTemplate jdbc;

    private Connection connection;
    private MessageConsumer consumer;

    @BeforeEach
    void subscribe() throws Exception {
        connection = connectionFactory.createConnection();
        Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
        consumer = session.createConsumer(session.createTopic(Addresses.EVENTS));
        connection.start();
    }

    @AfterEach
    void unsubscribe() throws Exception {
        connection.close();
    }

    @Test
    void placingAndValidatingPublishAvroEventsWithTheTraceIdOfTheRequest() throws Exception {
        String drug = new TestStock(jdbc).newDrug(true, 5);

        String created = mvc.perform(post("/orders").contentType(MediaType.APPLICATION_JSON)
                        .header("x-request-id", "trace-place-1")
                        .header("X-User-Id", "u-nurse-9")
                        .content("""
                                {"patientRef":"P-0099","drugCode":"%s","qty":2,"ward":"CARDIO-2","room":"214"}
                                """.formatted(drug)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String orderId = JsonPath.read(created, "$.id");

        Message placedMessage = nextMessageFor(orderId);
        assertThat(placedMessage.getStringProperty(MessageHeaders.EVENT_TYPE)).isEqualTo("OrderPlaced");
        assertThat(placedMessage.getStringProperty(MessageHeaders.TRACE_ID)).isEqualTo("trace-place-1");
        OrderPlaced placed = messages.fromMessage(placedMessage, OrderPlaced.class);
        assertThat(placed.getDrugCode()).isEqualTo(drug);
        assertThat(placed.getQty()).isEqualTo(2);
        assertThat(placed.getControlled()).isTrue();
        assertThat(placed.getNurseId()).isEqualTo("u-nurse-9");
        assertThat(placed.getWardLocation().getWard()).isEqualTo("CARDIO-2");
        assertThat(placed.getWardLocation().getRoom()).isEqualTo("214");
        assertThat(placed.getWardLocation().getPoint().getX()).isEqualTo(12);
        assertThat(placed.getWardLocation().getPoint().getY()).isEqualTo(3);

        mvc.perform(post("/pharmacy/orders/{id}/validate", orderId)
                        .header("x-request-id", "trace-validate-1")
                        .header("X-User-Id", "u-pharma-3"))
                .andExpect(status().isOk());

        Message validatedMessage = nextMessageFor(orderId);
        assertThat(validatedMessage.getStringProperty(MessageHeaders.EVENT_TYPE)).isEqualTo("OrderValidated");
        assertThat(validatedMessage.getStringProperty(MessageHeaders.TRACE_ID)).isEqualTo("trace-validate-1");
        OrderValidated validated = messages.fromMessage(validatedMessage, OrderValidated.class);
        assertThat(validated.getPharmacistId()).isEqualTo("u-pharma-3");
        assertThat(validated.getDestination()).isEqualTo(placed.getWardLocation());
        assertThat(validated.getControlled()).isTrue();
        assertThat(validated.getEventId()).isNotEqualTo(placed.getEventId());
    }

    @Test
    void aRejectedPlacementPublishesNothing() throws Exception {
        String drug = new TestStock(jdbc).newDrug(false, 1);

        mvc.perform(post("/orders").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"patientRef":"P-0100","drugCode":"%s","qty":3,"ward":"ER"}
                                """.formatted(drug)))
                .andExpect(status().isConflict());

        assertThat(consumer.receive(1_500)).isNull();
    }

    private Message nextMessageFor(String orderId) throws Exception {
        long deadline = System.currentTimeMillis() + RECEIVE_TIMEOUT_MS;
        while (System.currentTimeMillis() < deadline) {
            Message message = consumer.receive(Math.max(1, deadline - System.currentTimeMillis()));
            if (message != null && orderId.equals(message.getStringProperty(MessageHeaders.ORDER_ID))) {
                return message;
            }
        }
        throw new AssertionError("No event received on " + Addresses.EVENTS + " for order " + orderId);
    }
}
