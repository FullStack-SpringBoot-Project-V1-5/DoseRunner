package tn.rnu.eniso.doserunner.robotsim.pres;

import jakarta.jms.*;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.stereotype.Component;
import tn.rnu.eniso.doserunner.contracts.messaging.*;
import tn.rnu.eniso.doserunner.contracts.robot.MissionCommand;
import tn.rnu.eniso.doserunner.robotsim.facade.SimulationFleet;

@Component
public class MissionCommandListener {
    private final AvroJmsMessages messages;
    private final SimulationFleet fleet;
    public MissionCommandListener(AvroJmsMessages messages, SimulationFleet fleet) {
        this.messages = messages; this.fleet = fleet;
    }
    @JmsListener(destination = Addresses.ROBOT_COMMANDS, containerFactory = "simListenerFactory",
            selector = "robotId IN ('R-01', 'R-02', 'R-03')")
    public void onMessage(Message message) throws JMSException {
        var command = messages.fromMessage(message, MissionCommand.class);
        if (!"MissionCommand".equals(message.getStringProperty(MessageHeaders.EVENT_TYPE))
                || !command.getRobotId().equals(message.getStringProperty(MessageHeaders.ROBOT_ID))) {
            throw new IllegalArgumentException("Invalid eventType/robotId metadata");
        }
        String order = message.getStringProperty(MessageHeaders.ORDER_ID);
        String group = message.getStringProperty(MessageHeaders.GROUP_ID);
        if ((order != null && !order.equals(command.getOrderId()))
                || (group != null && !group.equals(command.getRobotId()))) {
            throw new IllegalArgumentException("Conflicting command metadata");
        }
        String trace = message.getStringProperty(MessageHeaders.TRACE_ID);
        boolean accepted = fleet.accept(command, trace, message.getJMSCorrelationID());
        org.slf4j.LoggerFactory.getLogger(MissionCommandListener.class).info(
                "MissionCommand {} robot={} mission={} seq={} traceId={}",
                accepted ? "accepted" : "replay acknowledged", command.getRobotId(), command.getMissionId(), command.getSeq(), trace);
    }
}
