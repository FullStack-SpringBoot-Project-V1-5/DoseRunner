package tn.rnu.eniso.doserunner.dispatch.pres;

import jakarta.jms.JMSException;
import jakarta.jms.Message;
import java.util.UUID;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.stereotype.Component;
import tn.rnu.eniso.doserunner.commons.trace.TraceHeaders;
import tn.rnu.eniso.doserunner.contracts.events.OrderValidated;
import tn.rnu.eniso.doserunner.contracts.messaging.*;
import tn.rnu.eniso.doserunner.dispatch.facade.OrderValidatedFacade;

@Component
public class OrderValidatedListener {
    private final AvroJmsMessages messages;
    private final OrderValidatedFacade facade;
    public OrderValidatedListener(AvroJmsMessages messages, OrderValidatedFacade facade) {
        this.messages = messages; this.facade = facade;
    }

    @JmsListener(destination = Addresses.EVENTS + "::" + Addresses.QUEUE_DISPATCH_SERVICE,
            selector = "eventType = 'OrderValidated'", containerFactory = "dispatchListenerFactory")
    public void onMessage(Message message) throws JMSException {
        var previous = MDC.getCopyOfContextMap();
        try {
            var event = messages.fromMessage(message, OrderValidated.class);
            String metadataOrder = message.getStringProperty(MessageHeaders.ORDER_ID);
            if (metadataOrder != null && !metadataOrder.equals(event.getOrderId())) {
                throw new IllegalArgumentException("Conflicting orderId metadata");
            }
            String trace = message.getStringProperty(MessageHeaders.TRACE_ID);
            boolean generated = trace == null || trace.isBlank();
            if (generated) trace = UUID.randomUUID().toString();
            MDC.put(TraceHeaders.MDC_TRACE_ID, trace);
            MDC.put(MessageHeaders.ORDER_ID, event.getOrderId());
            var log = LoggerFactory.getLogger(OrderValidatedListener.class);
            if (generated) log.info("Generated missing traceId for OrderValidated {}", event.getEventId());
            log.info("Received OrderValidated {} for order {}", event.getEventId(), event.getOrderId());
            facade.process(event, trace);
        } finally {
            if (previous == null) MDC.clear(); else MDC.setContextMap(previous);
        }
    }
}
