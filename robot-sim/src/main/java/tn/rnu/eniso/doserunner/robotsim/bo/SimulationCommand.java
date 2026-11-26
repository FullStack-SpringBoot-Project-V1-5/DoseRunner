package tn.rnu.eniso.doserunner.robotsim.bo;

import java.time.Instant;
import java.util.*;
import tn.rnu.eniso.doserunner.contracts.robot.MissionCommand;

/** Immutable validated copy: callers cannot mutate an accepted route or its replay identity. */
public record SimulationCommand(String missionId, String robotId, String orderId, int seq,
        List<Cell> waypoints, String compartmentCode, Instant issuedAt) {
    public record Cell(int x, int y) { }

    public static SimulationCommand from(MissionCommand c, int pharmacyX, int pharmacyY) {
        Objects.requireNonNull(c, "command");
        uuid(c.getMissionId()); uuid(c.getOrderId());
        if (c.getRobotId() == null || c.getRobotId().isBlank() || c.getSeq() < 1
                || c.getCompartmentCode() == null || c.getCompartmentCode().isBlank()
                || c.getIssuedAt() == null || c.getIssuedAt().isBefore(Instant.EPOCH)
                || c.getWaypoints() == null || c.getWaypoints().size() > 10000) {
            throw new IllegalArgumentException("Invalid required command fields");
        }
        var cells = new ArrayList<Cell>();
        var previous = new Cell(pharmacyX, pharmacyY);
        var visited = new HashSet<Cell>(); visited.add(previous);
        for (var p : c.getWaypoints()) {
            if (p == null) throw new IllegalArgumentException("Null waypoint");
            var cell = new Cell(p.getX(), p.getY());
            long distance = Math.abs((long) cell.x() - previous.x()) + Math.abs((long) cell.y() - previous.y());
            if (cell.x() < 0 || cell.y() < 0 || distance != 1 || !visited.add(cell)) {
                throw new IllegalArgumentException("Waypoints must be distinct adjacent nonnegative cells");
            }
            cells.add(cell); previous = cell;
        }
        return new SimulationCommand(c.getMissionId(), c.getRobotId(), c.getOrderId(), c.getSeq(),
                List.copyOf(cells), c.getCompartmentCode(), c.getIssuedAt());
    }
    private static void uuid(String value) {
        if (value == null || !UUID.fromString(value).toString().equalsIgnoreCase(value)) {
            throw new IllegalArgumentException("Expected canonical UUID");
        }
    }
}
