package tn.rnu.eniso.doserunner.robotlink;

import jakarta.jms.JMSException;
import java.io.IOException;
import java.util.Optional;
import tn.rnu.eniso.doserunner.contracts.robot.RobotTelemetry;

/** One robot's transport. Use one caller thread; close may be called from another thread. */
public interface RobotLink extends AutoCloseable {
    /** Positive, finite timeout in milliseconds; only one delivery may remain unsettled. */
    Optional<CommandDelivery> receive(long timeoutMillis) throws JMSException, IOException;
    /** Commit only after the application has accepted responsibility for execution. */
    void acknowledge(CommandDelivery delivery) throws JMSException;
    void rollback(CommandDelivery delivery) throws JMSException;
    /** Independent local JMS transaction; does not acknowledge the command. */
    void publishTelemetry(RobotTelemetry telemetry, CommandDelivery context) throws JMSException, IOException;
    @Override void close() throws JMSException;
}
