package tn.rnu.eniso.doserunner.dispatch.messaging;

import jakarta.jms.DeliveryMode;
import jakarta.jms.Session;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.jms.core.SessionCallback;
import org.springframework.stereotype.Component;
import tn.rnu.eniso.doserunner.contracts.events.RobotAssigned;
import tn.rnu.eniso.doserunner.contracts.robot.MissionCommand;
import tn.rnu.eniso.doserunner.contracts.messaging.*;
import tn.rnu.eniso.doserunner.dispatch.dao.DispatchMessageStore.Publication;

@Component
public class DispatchEventPublisher {
    private final JmsTemplate template;
    private final AvroMessageCodec codec;
    private final AvroJmsMessages messages;
    public DispatchEventPublisher(JmsTemplate template, AvroMessageCodec codec, AvroJmsMessages messages) {
        this.template = template; this.codec = codec; this.messages = messages;
    }

    public void publish(Publication publication) {
        try {
            template.execute((SessionCallback<Void>) session -> {
                try {
                    send(session, session.createTopic(Addresses.EVENTS), codec.decode(publication.assigned(), RobotAssigned.class),
                            publication, "assigned", false);
                    send(session, session.createQueue(Addresses.ROBOT_COMMANDS), codec.decode(publication.command(), MissionCommand.class),
                            publication, "command", true);
                    session.commit();
                    return null;
                } catch (jakarta.jms.JMSException | RuntimeException e) {
                    try { session.rollback(); } catch (jakarta.jms.JMSException rollback) { e.addSuppressed(rollback); }
                    throw e;
                }
            });
        } catch (org.springframework.jms.JmsException error) {
            for (Throwable cause = error; cause != null; cause = cause.getCause()) {
                if (cause instanceof org.apache.activemq.artemis.api.core.ActiveMQDuplicateIdException) {
                    // Both stable IDs belong to the same immutable, atomically published pair.
                    org.slf4j.LoggerFactory.getLogger(DispatchEventPublisher.class)
                            .info("Broker confirmed previously published mission {}", publication.missionId());
                    return;
                }
            }
            throw error;
        }
    }

    private void send(Session session, jakarta.jms.Destination destination, org.apache.avro.specific.SpecificRecord record,
                      Publication p, String kind, boolean command) throws jakarta.jms.JMSException {
        var message = messages.toMessage(session, record);
        message.setStringProperty(MessageHeaders.ORDER_ID, p.orderId().toString());
        message.setStringProperty(MessageHeaders.TRACE_ID, p.traceId());
        message.setStringProperty("_AMQ_DUPL_ID", "dispatch:" + p.missionId() + ":" + kind);
        if (command) {
            message.setStringProperty(MessageHeaders.ROBOT_ID, p.robotId());
            message.setStringProperty(MessageHeaders.GROUP_ID, p.robotId());
        }
        try (var producer = session.createProducer(destination)) {
            producer.setDeliveryMode(DeliveryMode.PERSISTENT);
            producer.send(message);
        }
    }
}
