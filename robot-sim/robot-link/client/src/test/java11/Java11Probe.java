import tn.rnu.eniso.doserunner.robotlink.*;
import tn.rnu.eniso.doserunner.contracts.robot.RobotTelemetry;
import org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory;

public final class Java11Probe {
    public static void main(String[] args) throws Exception {
        if (Runtime.version().feature() != 11) throw new AssertionError("Not running Java 11");
        try (ActiveMQConnectionFactory factory = new ActiveMQConnectionFactory(args[0]);
             RobotLink link = new JmsRobotLink(factory, "JAVA11", "c007", "c007-test")) {
            CommandDelivery first = link.receive(15000).orElseThrow();
            if (first.deliveryCount() != 1) throw new AssertionError("Initial delivery count");
            link.rollback(first);
            CommandDelivery retry = link.receive(15000).orElseThrow();
            if (retry.deliveryCount() != 2 || !first.command().equals(retry.command())) throw new AssertionError("Redelivery");
            link.publishTelemetry(new RobotTelemetry("JAVA11", 1, 0, 99), retry);
            link.acknowledge(retry);
            if (link.receive(200).isPresent()) throw new AssertionError("Unexpected delivery after commit");
        }
        System.out.println("JAVA11_LINK_OK " + System.getProperty("java.version"));
    }
}
