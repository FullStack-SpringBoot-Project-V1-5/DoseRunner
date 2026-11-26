package tn.rnu.eniso.doserunner.robotlink;

import org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory;
import tn.rnu.eniso.doserunner.contracts.common.GridPoint;
import tn.rnu.eniso.doserunner.contracts.robot.RobotTelemetry;

/** Synthetic transport demo, not a motor controller. Receives one command, publishes its final cell. */
public final class RobotLinkExample {
    private RobotLinkExample() { }
    public static void main(String[] args) throws Exception {
        if (args.length != 2) throw new IllegalArgumentException("Usage: RobotLinkExample <broker-url> <robotId>");
        try (ActiveMQConnectionFactory factory = new ActiveMQConnectionFactory(args[0]);
             RobotLink link = new JmsRobotLink(factory, args[1], System.getenv("ARTEMIS_USER"), System.getenv("ARTEMIS_PASSWORD"))) {
            CommandDelivery delivery = link.receive(30000).orElseThrow(() -> new IllegalStateException("No command in 30s"));
            try {
                System.out.println("Received " + delivery.command().getMissionId() + " for " + args[1]);
                if (!delivery.command().getWaypoints().isEmpty()) {
                    GridPoint last = delivery.command().getWaypoints().get(delivery.command().getWaypoints().size() - 1);
                    link.publishTelemetry(new RobotTelemetry(args[1], last.getX(), last.getY(), 100), delivery);
                }
                link.acknowledge(delivery);
            } catch (Exception error) {
                try { link.rollback(delivery); } catch (Exception rollback) { error.addSuppressed(rollback); }
                throw error;
            }
        }
    }
}
