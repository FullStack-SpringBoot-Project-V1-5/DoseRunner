package tn.rnu.eniso.doserunner.order.messaging;

import jakarta.jms.BytesMessage;
import jakarta.jms.Session;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.MDC;
import org.springframework.jms.UncategorizedJmsException;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.jms.core.MessageCreator;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tn.rnu.eniso.doserunner.commons.trace.TraceHeaders;
import tn.rnu.eniso.doserunner.contracts.events.OrderPlaced;
import tn.rnu.eniso.doserunner.contracts.events.OrderValidated;
import tn.rnu.eniso.doserunner.contracts.messaging.Addresses;
import tn.rnu.eniso.doserunner.contracts.messaging.AvroJmsMessages;
import tn.rnu.eniso.doserunner.contracts.messaging.AvroMessageCodec;
import tn.rnu.eniso.doserunner.contracts.messaging.MessageHeaders;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OrderEventPublisherTest {

    private static final Instant NOW = Instant.parse("2026-11-20T10:00:00.123456Z");
    private static final DeliveryDestination CARDIO = new DeliveryDestination("CARDIO-2", "214", 12, 3);

    private final AvroMessageCodec codec = AvroMessageCodec.forContracts();
    private final JmsTemplate jmsTemplate = mock(JmsTemplate.class);
    private final OrderEventPublisher publisher = new OrderEventPublisher(jmsTemplate, new AvroJmsMessages(codec));

    @AfterEach
    void cleanUp() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
        MDC.clear();
    }

    @Test
    void sendsImmediatelyWithoutATransactionAndSetsTheHeaders() throws Exception {
        MDC.put(TraceHeaders.MDC_TRACE_ID, "trace-42");
        UUID orderId = UUID.randomUUID();

        publisher.publish(new OrderValidatedNotice(orderId, "u-pharma", CARDIO, true, NOW));

        BytesMessage message = captureSentMessage();
        verify(message).setStringProperty(MessageHeaders.EVENT_TYPE, "OrderValidated");
        verify(message).setStringProperty(MessageHeaders.ORDER_ID, orderId.toString());
        verify(message).setStringProperty(MessageHeaders.TRACE_ID, "trace-42");
        OrderValidated event = codec.decode(writtenBody(message), OrderValidated.class);
        assertThat(event.getOrderId()).isEqualTo(orderId.toString());
        assertThat(event.getDestination().getPoint().getX()).isEqualTo(12);
        assertThat(event.getDestination().getRoom()).isEqualTo("214");
        assertThat(event.getOccurredAt()).isEqualTo(Instant.parse("2026-11-20T10:00:00.123Z"));
        assertThat(UUID.fromString(event.getEventId())).isNotNull();
    }

    @Test
    void waitsForTheCommitInsideATransaction() throws Exception {
        TransactionSynchronizationManager.initSynchronization();
        UUID orderId = UUID.randomUUID();

        publisher.publish(new OrderPlacedNotice(orderId, "P-0042", "MORPH-10", 2, CARDIO, true, "u-nurse", NOW));

        verify(jmsTemplate, never()).send(any(String.class), any(MessageCreator.class));
        for (TransactionSynchronization synchronization : TransactionSynchronizationManager.getSynchronizations()) {
            synchronization.afterCommit();
        }
        OrderPlaced event = codec.decode(writtenBody(captureSentMessage()), OrderPlaced.class);
        assertThat(event.getOrderId()).isEqualTo(orderId.toString());
        assertThat(event.getControlled()).isTrue();
        assertThat(event.getNurseId()).isEqualTo("u-nurse");
    }

    @Test
    void neverSendsWhenTheTransactionRollsBack() {
        TransactionSynchronizationManager.initSynchronization();

        publisher.publish(new OrderPlacedNotice(UUID.randomUUID(), "P-1", "PARA-500", 1, CARDIO, false, "u", NOW));
        for (TransactionSynchronization synchronization : TransactionSynchronizationManager.getSynchronizations()) {
            synchronization.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);
        }

        verify(jmsTemplate, never()).send(any(String.class), any(MessageCreator.class));
    }

    @Test
    void aBrokerFailureIsLoggedAndNotPropagated() {
        doThrow(new UncategorizedJmsException("broker down"))
                .when(jmsTemplate).send(eq(Addresses.EVENTS), any(MessageCreator.class));

        assertThatCode(() -> publisher.publish(new OrderValidatedNotice(UUID.randomUUID(), "u", CARDIO, false, NOW)))
                .doesNotThrowAnyException();
    }

    private BytesMessage captureSentMessage() throws Exception {
        ArgumentCaptor<MessageCreator> creator = ArgumentCaptor.forClass(MessageCreator.class);
        verify(jmsTemplate).send(eq(Addresses.EVENTS), creator.capture());
        Session session = mock(Session.class);
        BytesMessage message = mock(BytesMessage.class);
        when(session.createBytesMessage()).thenReturn(message);
        assertThat(creator.getValue().createMessage(session)).isSameAs(message);
        return message;
    }

    private static byte[] writtenBody(BytesMessage message) throws Exception {
        ArgumentCaptor<byte[]> body = ArgumentCaptor.forClass(byte[].class);
        verify(message).writeBytes(body.capture());
        return body.getValue();
    }
}
