package tn.rnu.eniso.doserunner.dispatch.facade;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tn.rnu.eniso.doserunner.contracts.robot.RobotTelemetry;
import tn.rnu.eniso.doserunner.dispatch.dao.RobotRepository;

@Service
public class RobotTelemetryFacade {
    private final RobotRepository robots;

    public RobotTelemetryFacade(RobotRepository robots) {
        this.robots = robots;
    }

    @Transactional
    public void update(RobotTelemetry telemetry) {
        String id = telemetry.getRobotId();
        if (id == null || id.isBlank() || id.length() > 64 || telemetry.getX() < 0 || telemetry.getY() < 0
                || telemetry.getBattery() < 0 || telemetry.getBattery() > 100) {
            throw new IllegalArgumentException("Invalid robot telemetry");
        }
        // shortcut: v1 has no timestamp/sequence; revisit stale replay rejection with versioned telemetry.
        // Update only measured fields so concurrent assignment cannot lose its BUSY status.
        if (robots.updateTelemetry(id, telemetry.getX(), telemetry.getY(), telemetry.getBattery()) != 1) {
            throw new IllegalArgumentException("Unknown telemetry robot: " + id);
        }
    }
}
