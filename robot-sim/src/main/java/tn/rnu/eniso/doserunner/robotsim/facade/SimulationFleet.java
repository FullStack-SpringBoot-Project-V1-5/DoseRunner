package tn.rnu.eniso.doserunner.robotsim.facade;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.LongSupplier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tn.rnu.eniso.doserunner.contracts.robot.MissionCommand;
import tn.rnu.eniso.doserunner.robotsim.bo.*;
import tn.rnu.eniso.doserunner.robotsim.messaging.TelemetryPublisher;

@Service
public class SimulationFleet {
    private final Map<String, SimulatedRobot> robots;
    private final TelemetryPublisher publisher;
    private final LongSupplier clock;
    private final int pharmacyX, pharmacyY;
    private final boolean scheduling;
    private ScheduledExecutorService executor;

    public SimulationFleet(TelemetryPublisher publisher, LongSupplier clock,
            @Value("${sim.pharmacy-x:0}") int x, @Value("${sim.pharmacy-y:0}") int y,
            @Value("${sim.scheduling-enabled:true}") boolean scheduling) {
        if (x < 0 || y < 0) throw new IllegalArgumentException("Invalid pharmacy coordinates");
        this.publisher = publisher; this.clock = clock; pharmacyX = x; pharmacyY = y; this.scheduling = scheduling;
        var fleet = new LinkedHashMap<String, SimulatedRobot>();
        for (String id : List.of("R-01", "R-02", "R-03")) fleet.put(id, new SimulatedRobot(id, x, y));
        robots = Collections.unmodifiableMap(fleet);
    }
    @PostConstruct public void start() {
        if (!scheduling) return;
        executor = Executors.newScheduledThreadPool(3);
        for (String id : robots.keySet()) executor.scheduleWithFixedDelay(() -> {
            try { tick(id); }
            catch (RuntimeException error) {
                org.slf4j.LoggerFactory.getLogger(SimulationFleet.class).error("Telemetry failed for {}; movement paused for retry", id, error);
            }
        }, 50, 50, TimeUnit.MILLISECONDS);
    }
    public boolean accept(MissionCommand command, String trace, String correlation) {
        var copy = SimulationCommand.from(command, pharmacyX, pharmacyY);
        return robot(copy.robotId()).accept(new SimulatedRobot.Execution(copy, trace, correlation), clock);
    }
    public void tick(String id) {
        var robot = robot(id);
        synchronized (robot) {
            var execution = robot.active();
            robot.tick(clock, telemetry -> publisher.publish(telemetry, execution));
        }
    }
    private SimulatedRobot robot(String id) {
        var robot = robots.get(id);
        if (robot == null) throw new IllegalArgumentException("Unknown robot: " + id);
        return robot;
    }
    public SimulatedRobot.Snapshot snapshot(String id) { return robot(id).snapshot(); }
    @PreDestroy public void close() {
        if (executor != null) executor.shutdownNow();
        robots.values().forEach(SimulatedRobot::stop);
    }
}
