package tn.rnu.eniso.doserunner.robotsim.messaging;

import org.springframework.jms.core.JmsTemplate;
import org.springframework.stereotype.Component;
import tn.rnu.eniso.doserunner.contracts.messaging.*;
import tn.rnu.eniso.doserunner.contracts.robot.RobotTelemetry;
import tn.rnu.eniso.doserunner.robotsim.bo.SimulatedRobot.Execution;

@Component
public class TelemetryPublisher {
    private final JmsTemplate template;
    private final AvroJmsMessages messages;
    public TelemetryPublisher(JmsTemplate template, AvroJmsMessages messages) {
        this.template = template; this.messages = messages;
    }
    public void publish(RobotTelemetry telemetry, Execution execution) {
        template.send(Addresses.ROBOT_TELEMETRY, session -> {
            var message = messages.toMessage(session, telemetry);
            message.setStringProperty(MessageHeaders.ROBOT_ID, telemetry.getRobotId());
            message.setStringProperty(MessageHeaders.ORDER_ID, execution.command().orderId());
            if (execution.traceId() != null) message.setStringProperty(MessageHeaders.TRACE_ID, execution.traceId());
            message.setJMSCorrelationID(execution.correlationId());
            return message;
        });
    }
}
