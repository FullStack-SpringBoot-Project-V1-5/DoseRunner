package tn.rnu.eniso.doserunner.order.messaging;

import jakarta.jms.BytesMessage;
import org.apache.avro.specific.SpecificRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jms.JmsException;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tn.rnu.eniso.doserunner.commons.trace.TraceHeaders;
import tn.rnu.eniso.doserunner.contracts.messaging.Addresses;
import tn.rnu.eniso.doserunner.contracts.messaging.AvroJmsMessages;
import tn.rnu.eniso.doserunner.contracts.messaging.AvroMessageCodec;
import tn.rnu.eniso.doserunner.contracts.messaging.MessageHeaders;
import tn.rnu.eniso.doserunner.order.config.MessagingConfig;

@Component
public class OrderEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(OrderEventPublisher.class);

    private final JmsTemplate jmsTemplate;
    private final AvroJmsMessages messages;

    public OrderEventPublisher(@Qualifier(MessagingConfig.EVENTS_JMS_TEMPLATE) JmsTemplate jmsTemplate,
                               AvroJmsMessages messages) {
        this.jmsTemplate = jmsTemplate;
        this.messages = messages;
    }

    public void publish(OrderPlacedNotice notice) {
        sendAfterCommit(OrderEventMapper.toAvro(notice), notice.orderId().toString());
    }

    public void publish(OrderValidatedNotice notice) {
        sendAfterCommit(OrderEventMapper.toAvro(notice), notice.orderId().toString());
    }

    private void sendAfterCommit(SpecificRecord event, String orderId) {
        String traceId = MDC.get(TraceHeaders.MDC_TRACE_ID);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    send(event, orderId, traceId);
                }
            });
        } else {
            send(event, orderId, traceId);
        }
    }

    private void send(SpecificRecord event, String orderId, String traceId) {
        String eventType = AvroMessageCodec.eventType(event);
        try {
            jmsTemplate.send(Addresses.EVENTS, session -> {
                BytesMessage message = messages.toMessage(session, event);
                message.setStringProperty(MessageHeaders.ORDER_ID, orderId);
                if (traceId != null) {
                    message.setStringProperty(MessageHeaders.TRACE_ID, traceId);
                }
                return message;
            });
            log.info("Published {} for order {} on {}", eventType, orderId, Addresses.EVENTS);
        } catch (JmsException e) {
            log.error("Could not publish {} for order {} on {}", eventType, orderId, Addresses.EVENTS, e);
        }
    }
}
