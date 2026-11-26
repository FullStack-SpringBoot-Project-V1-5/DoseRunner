package tn.rnu.eniso.doserunner.robotlink;

import jakarta.jms.BytesMessage;
import jakarta.jms.Connection;
import jakarta.jms.ConnectionFactory;
import jakarta.jms.DeliveryMode;
import jakarta.jms.JMSException;
import jakarta.jms.Message;
import jakarta.jms.MessageConsumer;
import jakarta.jms.MessageProducer;
import jakarta.jms.Session;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import tn.rnu.eniso.doserunner.contracts.AvroTrust;
import tn.rnu.eniso.doserunner.contracts.messaging.Addresses;
import tn.rnu.eniso.doserunner.contracts.messaging.MessageHeaders;
import tn.rnu.eniso.doserunner.contracts.robot.MissionCommand;
import tn.rnu.eniso.doserunner.contracts.robot.RobotTelemetry;

/** Spring-free, single-caller transport. Execution and durable replay protection belong to its caller. */
public final class JmsRobotLink implements RobotLink {
    private final String robotId;
    private final Connection connection;
    private final Session commands;
    private final Session telemetry;
    private final MessageConsumer consumer;
    private final MessageProducer producer;
    private CommandDelivery pending;
    private volatile boolean closed;

    /** The caller owns the factory. Closing this link closes its connection and sessions only. */
    public JmsRobotLink(ConnectionFactory factory, String robotId, String user, String password) throws JMSException {
        if (robotId == null || !robotId.matches("[A-Za-z0-9_-]{1,64}"))
            throw new IllegalArgumentException("robotId must be 1..64 letters, digits, underscores or hyphens");
        this.robotId = robotId;
        AvroTrust.install();
        connection = Objects.requireNonNull(factory, "factory").createConnection(user, password);
        try {
            commands = connection.createSession(true, Session.SESSION_TRANSACTED);
            telemetry = connection.createSession(true, Session.SESSION_TRANSACTED);
            consumer = commands.createConsumer(commands.createQueue(Addresses.ROBOT_COMMANDS),
                    MessageHeaders.ROBOT_ID + " = '" + robotId + "'");
            producer = telemetry.createProducer(telemetry.createTopic(Addresses.ROBOT_TELEMETRY));
            connection.start();
        } catch (JMSException | RuntimeException error) {
            try { connection.close(); } catch (JMSException cleanup) { error.addSuppressed(cleanup); }
            throw error;
        }
    }

    @Override public Optional<CommandDelivery> receive(long timeoutMillis) throws JMSException, IOException {
        ensureOpen();
        if (timeoutMillis <= 0) throw new IllegalArgumentException("timeoutMillis must be positive");
        if (pending != null) throw new IllegalStateException("Settle the pending delivery first");
        Message message = consumer.receive(timeoutMillis);
        if (message == null) return Optional.empty();
        try {
            if (!(message instanceof BytesMessage)) throw new IOException("Expected Avro BytesMessage");
            BytesMessage bytes = (BytesMessage) message;
            bytes.reset();
            long size = bytes.getBodyLength();
            if (size < 10 || size > 1_048_576) throw new IOException("Command body outside 10..1048576 bytes");
            byte[] body = new byte[(int) size];
            if (bytes.readBytes(body) != body.length) throw new IOException("Truncated command body");
            // Generated Avro APIs use the same single-object encoding as the shared Java 25 codec.
            MissionCommand command;
            try { command = MissionCommand.fromByteBuffer(ByteBuffer.wrap(body)); }
            catch (RuntimeException error) { throw new IOException("Invalid Avro MissionCommand", error); }
            validate(command, message);
            pending = new CommandDelivery(this, command, message.getStringProperty(MessageHeaders.TRACE_ID),
                    message.getJMSCorrelationID(), message.getIntProperty("JMSXDeliveryCount"));
            return Optional.of(pending);
        } catch (JMSException | IOException | RuntimeException error) {
            rollbackAfterFailure(commands, error);
            throw error;
        }
    }

    private void validate(MissionCommand command, Message message) throws JMSException, IOException {
        if (!robotId.equals(command.getRobotId()) || !robotId.equals(message.getStringProperty(MessageHeaders.ROBOT_ID))
                || !"MissionCommand".equals(message.getStringProperty(MessageHeaders.EVENT_TYPE)))
            throw new IOException("Command target or eventType mismatch");
        if (command.getSeq() < 1 || command.getCompartmentCode() == null || command.getCompartmentCode().isBlank()
                || command.getIssuedAt() == null || command.getIssuedAt().toEpochMilli() < 0
                || command.getWaypoints() == null || command.getWaypoints().size() > 10000)
            throw new IOException("Invalid command fields");
        uuid(command.getMissionId()); uuid(command.getOrderId());
        for (tn.rnu.eniso.doserunner.contracts.common.GridPoint cell : command.getWaypoints()) {
            if (cell == null || cell.getX() < 0 || cell.getY() < 0) throw new IOException("Invalid waypoint");
        }
        String order = message.getStringProperty(MessageHeaders.ORDER_ID);
        String group = message.getStringProperty(MessageHeaders.GROUP_ID);
        if ((order != null && !order.equals(command.getOrderId())) || (group != null && !group.equals(robotId)))
            throw new IOException("Command metadata mismatch");
    }

    private static void uuid(String value) throws IOException {
        try {
            if (!UUID.fromString(value).toString().equalsIgnoreCase(value)) throw new IllegalArgumentException();
        } catch (IllegalArgumentException | NullPointerException error) { throw new IOException("Invalid UUID", error); }
    }

    @Override public void acknowledge(CommandDelivery delivery) throws JMSException {
        requirePending(delivery);
        try { commands.commit(); }
        catch (JMSException error) { closeAfterFailure(error); throw error; }
        pending = null;
    }
    @Override public void rollback(CommandDelivery delivery) throws JMSException {
        requirePending(delivery);
        try { commands.rollback(); }
        catch (JMSException error) { closeAfterFailure(error); throw error; }
        pending = null;
    }
    private void requirePending(CommandDelivery delivery) {
        ensureOpen();
        if (delivery == null || delivery != pending) throw new IllegalArgumentException("Not this link's pending delivery");
    }

    @Override public void publishTelemetry(RobotTelemetry record, CommandDelivery context) throws JMSException, IOException {
        ensureOpen();
        Objects.requireNonNull(record, "telemetry"); Objects.requireNonNull(context, "context");
        if (!context.belongsTo(this) || !robotId.equals(record.getRobotId()) || record.getX() < 0 || record.getY() < 0
                || record.getBattery() < 0 || record.getBattery() > 100)
            throw new IllegalArgumentException("Invalid telemetry target, position or battery");
        try {
            ByteBuffer encoded = record.toByteBuffer();
            byte[] body = new byte[encoded.remaining()]; encoded.get(body);
            BytesMessage message = telemetry.createBytesMessage(); message.writeBytes(body);
            message.setStringProperty(MessageHeaders.EVENT_TYPE, "RobotTelemetry");
            message.setStringProperty(MessageHeaders.ROBOT_ID, robotId);
            message.setStringProperty(MessageHeaders.ORDER_ID, context.orderId());
            if (context.traceId() != null) message.setStringProperty(MessageHeaders.TRACE_ID, context.traceId());
            message.setJMSCorrelationID(context.correlationId());
            producer.send(message, DeliveryMode.PERSISTENT, Message.DEFAULT_PRIORITY, Message.DEFAULT_TIME_TO_LIVE);
            telemetry.commit();
        } catch (JMSException | IOException | RuntimeException error) {
            rollbackAfterFailure(telemetry, error);
            throw error;
        }
    }

    private void rollbackAfterFailure(Session session, Exception error) {
        try { session.rollback(); }
        catch (JMSException rollback) {
            error.addSuppressed(rollback);
            // An uncertain transaction must never be reused to acknowledge later messages.
            closeAfterFailure(error);
        }
    }

    private void closeAfterFailure(Exception error) {
        try { close(); } catch (JMSException cleanup) { error.addSuppressed(cleanup); }
    }

    private void ensureOpen() { if (closed) throw new IllegalStateException("RobotLink is closed"); }
    @Override public void close() throws JMSException {
        closed = true;
        connection.close(); // Closing an unsettled session rolls its command transaction back.
    }
}
