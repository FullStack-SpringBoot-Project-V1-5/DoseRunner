package tn.rnu.eniso.doserunner.robotlink;

import tn.rnu.eniso.doserunner.contracts.robot.MissionCommand;

/** Delivery token and original metadata; settle only on its originating link. */
public final class CommandDelivery {
    private final RobotLink owner;
    private final MissionCommand command;
    private final String orderId;
    private final String traceId;
    private final String correlationId;
    private final int deliveryCount;

    CommandDelivery(RobotLink owner, MissionCommand command, String traceId, String correlationId, int deliveryCount) {
        this.owner = owner;
        this.command = command;
        this.orderId = command.getOrderId();
        this.traceId = traceId;
        this.correlationId = correlationId;
        this.deliveryCount = deliveryCount;
    }
    boolean belongsTo(RobotLink link) { return owner == link; }
    public MissionCommand command() { return command; }
    public String orderId() { return orderId; }
    public String traceId() { return traceId; }
    public String correlationId() { return correlationId; }
    public int deliveryCount() { return deliveryCount; }
}
