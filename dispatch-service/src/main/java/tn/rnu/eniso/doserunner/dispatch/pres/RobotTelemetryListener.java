package tn.rnu.eniso.doserunner.dispatch.pres;

import jakarta.jms.JMSException;
import jakarta.jms.Message;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.stereotype.Component;
import tn.rnu.eniso.doserunner.commons.trace.TraceHeaders;
import tn.rnu.eniso.doserunner.contracts.messaging.Addresses;
import tn.rnu.eniso.doserunner.contracts.messaging.AvroJmsMessages;
import tn.rnu.eniso.doserunner.contracts.messaging.MessageHeaders;
import tn.rnu.eniso.doserunner.contracts.robot.RobotTelemetry;
import tn.rnu.eniso.doserunner.dispatch.facade.RobotTelemetryFacade;

@Component
public class RobotTelemetryListener {
    private final AvroJmsMessages messages;
    private final RobotTelemetryFacade facade;

    public RobotTelemetryListener(AvroJmsMessages messages, RobotTelemetryFacade facade) {
        this.messages = messages;
        this.facade = facade;
    }

    @JmsListener(destination = Addresses.ROBOT_TELEMETRY + "::" + Addresses.QUEUE_DISPATCH_TELEMETRY,
            containerFactory = "dispatchListenerFactory")
    public void onMessage(Message message) throws JMSException {
        var previous = MDC.getCopyOfContextMap();
        try {
            var telemetry = messages.fromMessage(message, RobotTelemetry.class);
            String robotId = message.getStringProperty(MessageHeaders.ROBOT_ID);
            String type = message.getStringProperty(MessageHeaders.EVENT_TYPE);
            if ((robotId != null && !robotId.equals(telemetry.getRobotId()))
                    || (type != null && !type.equals("RobotTelemetry"))) {
                throw new IllegalArgumentException("Conflicting telemetry metadata");
            }
            String trace = message.getStringProperty(MessageHeaders.TRACE_ID);
            if (trace != null && !trace.isBlank()) MDC.put(TraceHeaders.MDC_TRACE_ID, trace);
            facade.update(telemetry);
            LoggerFactory.getLogger(RobotTelemetryListener.class).info("Updated telemetry for {} at ({}, {}), battery {}",
                    telemetry.getRobotId(), telemetry.getX(), telemetry.getY(), telemetry.getBattery());
        } finally {
            if (previous == null) MDC.clear(); else MDC.setContextMap(previous);
        }
    }
}
