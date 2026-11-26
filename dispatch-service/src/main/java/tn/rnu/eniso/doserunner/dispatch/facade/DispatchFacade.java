package tn.rnu.eniso.doserunner.dispatch.facade;

import jakarta.persistence.EntityManager;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import tn.rnu.eniso.doserunner.dispatch.bo.DispatchStrategy;
import tn.rnu.eniso.doserunner.dispatch.bo.RobotCandidate;
import tn.rnu.eniso.doserunner.dispatch.dao.*;

@Service
public class DispatchFacade {
    private final RobotRepository robots;
    private final MissionRepository missions;
    private final DispatchStrategy strategy;
    private final EntityManager entityManager;

    public DispatchFacade(RobotRepository robots, MissionRepository missions,
                          DispatchStrategy strategy, EntityManager entityManager) {
        this.robots = robots;
        this.missions = missions;
        this.strategy = strategy;
        this.entityManager = entityManager;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Optional<Mission> assignMission(UUID orderId, int pickupX, int pickupY) {
        Objects.requireNonNull(orderId, "orderId");
        if (pickupX < 0 || pickupY < 0) {
            throw new IllegalArgumentException("Pickup coordinates must be nonnegative");
        }
        // shortcut: serialize the small Week 1 fleet, revisit locking when throughput matters.
        var fleet = robots.lockFleet();
        // A caller may already have loaded these entities in the current persistence context.
        fleet.forEach(entityManager::refresh);
        var previous = missions.findByOrderId(orderId);
        var assigned = previous.stream().filter(mission -> mission.getStatus() == MissionStatus.ASSIGNED)
                .findFirst();
        if (assigned.isPresent()) {
            return assigned;
        }
        if (!previous.isEmpty()) {
            throw new IllegalStateException("Order already has a terminal mission; explicit reassignment required");
        }
        var candidates = fleet.stream().map(robot -> new RobotCandidate(robot.getId(), robot.getX(),
                robot.getY(), robot.getBattery(), robot.getStatus())).toList();
        return strategy.select(candidates, pickupX, pickupY).map(selected -> {
            Robot robot = fleet.stream().filter(candidate -> candidate.getId().equals(selected.id()))
                    .findFirst().orElseThrow();
            robot.setStatus(RobotStatus.BUSY);
            return missions.save(new Mission(UUID.randomUUID(), orderId, robot, MissionStatus.ASSIGNED));
        });
    }
}
