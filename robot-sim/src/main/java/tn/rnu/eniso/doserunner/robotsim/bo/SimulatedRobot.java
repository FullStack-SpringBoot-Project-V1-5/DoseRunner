package tn.rnu.eniso.doserunner.robotsim.bo;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import tn.rnu.eniso.doserunner.contracts.robot.RobotTelemetry;

/** All state and telemetry I/O for one robot are serialized on this monitor. */
public final class SimulatedRobot {
    public record Execution(SimulationCommand command, String traceId, String correlationId) { }
    public record Snapshot(int x, int y, int battery, String activeMission, int lastAcceptedSeq, boolean waiting) { }
    private static final long SECOND = 1_000_000_000L;
    private final String id;
    private final int pharmacyX, pharmacyY;
    // shortcut: replay history is lost on restart, add durable receipts before crash-safe execution.
    private final Map<UUID, SimulationCommand> accepted = new HashMap<>();
    private int x, y, battery = 100, lastSeq, index, failures;
    private long due;
    private Execution active, waiting;
    private boolean stopped;

    public SimulatedRobot(String id, int pharmacyX, int pharmacyY) {
        this.id = id; this.pharmacyX = pharmacyX; this.pharmacyY = pharmacyY;
        x = pharmacyX; y = pharmacyY;
    }
    public synchronized boolean accept(Execution execution, LongSupplier clock) {
        long now = clock.getAsLong();
        var c = execution.command();
        if (stopped) throw new IllegalStateException("Simulator stopped");
        if (!id.equals(c.robotId())) throw new IllegalArgumentException("Wrong robot");
        var missionId = UUID.fromString(c.missionId());
        var old = accepted.get(missionId);
        if (old != null) {
            if (old.equals(c)) return false;
            throw new IllegalArgumentException("Conflicting mission replay");
        }
        if (c.seq() <= lastSeq) throw new IllegalArgumentException("Unrecognized stale sequence");
        if (active != null && waiting != null) throw new IllegalStateException("Robot waiting slot full");
        accepted.put(missionId, c); lastSeq = c.seq();
        if (active == null) start(execution, now); else waiting = execution;
        return true;
    }
    private void start(Execution execution, long now) {
        x = pharmacyX; y = pharmacyY; index = 0; failures = 0;
        active = execution; due = now + SECOND;
        if (execution.command().waypoints().isEmpty()) complete(now);
    }
    public synchronized void tick(LongSupplier clock, Consumer<RobotTelemetry> publish) {
        long now = clock.getAsLong();
        if (stopped || active == null || now < due) return;
        var cell = active.command().waypoints().get(index);
        var telemetry = new RobotTelemetry(id, cell.x(), cell.y(), Math.max(0, battery - 1));
        try {
            publish.accept(telemetry);
        } catch (RuntimeException error) {
            due = clock.getAsLong() + SECOND * (1L << failures);
            failures = Math.min(failures + 1, 3);
            throw error;
        }
        x = cell.x(); y = cell.y(); battery = telemetry.getBattery(); index++; failures = 0;
        long finished = clock.getAsLong();
        due = finished + SECOND;
        if (index == active.command().waypoints().size()) complete(finished);
    }
    private void complete(long now) {
        org.slf4j.LoggerFactory.getLogger(SimulatedRobot.class).info("Mission {} completed by {} traceId={} correlationId={}",
                active.command().missionId(), id, active.traceId(), active.correlationId());
        active = null;
        if (waiting != null) { var next = waiting; waiting = null; start(next, now); }
    }
    public synchronized Execution active() { return active; }
    public synchronized Snapshot snapshot() {
        return new Snapshot(x, y, battery, active == null ? null : active.command().missionId(), lastSeq, waiting != null);
    }
    public synchronized void stop() { stopped = true; active = null; waiting = null; }
}
