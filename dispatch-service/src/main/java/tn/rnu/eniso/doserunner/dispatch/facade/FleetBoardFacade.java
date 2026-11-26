package tn.rnu.eniso.doserunner.dispatch.facade;

import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import tn.rnu.eniso.doserunner.dispatch.dao.MissionRepository;
import tn.rnu.eniso.doserunner.dispatch.dao.MissionStatus;
import tn.rnu.eniso.doserunner.dispatch.dao.RobotRepository;

@Service
public class FleetBoardFacade {
    private final RobotRepository robots;
    private final MissionRepository missions;

    public FleetBoardFacade(RobotRepository robots, MissionRepository missions) {
        this.robots = robots;
        this.missions = missions;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Board snapshot() {
        var fleet = robots.findAll(Sort.by("id")).stream().map(r ->
                new RobotRow(r.getId(), r.getX(), r.getY(), r.getBattery(), r.getStatus().name())).toList();
        var active = missions.findByStatusOrderByIdAsc(MissionStatus.ASSIGNED).stream().map(m ->
                new MissionRow(m.getId(), m.getOrderId(), m.getRobot().getId(), m.getStatus().name())).toList();
        return new Board(fleet, active);
    }

    public record Board(List<RobotRow> robots, List<MissionRow> missions) {}
    public record RobotRow(String id, int x, int y, int battery, String status) {}
    public record MissionRow(UUID id, UUID orderId, String robotId, String status) {}
}
