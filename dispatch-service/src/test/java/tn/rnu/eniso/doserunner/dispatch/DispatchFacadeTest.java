package tn.rnu.eniso.doserunner.dispatch;

import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import tn.rnu.eniso.doserunner.dispatch.bo.NearestIdleRobotStrategy;
import tn.rnu.eniso.doserunner.dispatch.dao.*;
import tn.rnu.eniso.doserunner.dispatch.facade.DispatchFacade;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class DispatchFacadeTest {
    final RobotRepository robots = mock(RobotRepository.class);
    final MissionRepository missions = mock(MissionRepository.class);
    final EntityManager entityManager = mock(EntityManager.class);
    final DispatchFacade facade = new DispatchFacade(robots, missions, new NearestIdleRobotStrategy(), entityManager);
    final UUID orderId = UUID.randomUUID();
    final Robot robot = new Robot("R", 0, 0, 100, RobotStatus.IDLE);

    @BeforeEach
    void setup() {
        when(robots.lockFleet()).thenReturn(List.of(robot));
        when(missions.findByOrderId(orderId)).thenReturn(List.of());
        when(missions.save(any(Mission.class))).thenAnswer(call -> call.getArgument(0));
    }

    @Test
    void assignsAndChecksPreviousMissionsOnlyAfterLocking() {
        Mission assigned = facade.assignMission(orderId, 0, 0).orElseThrow();
        assertThat(assigned.getOrderId()).isEqualTo(orderId);
        assertThat(assigned.getStatus()).isEqualTo(MissionStatus.ASSIGNED);
        assertThat(assigned.getRobot()).isSameAs(robot);
        assertThat(robot.getStatus()).isEqualTo(RobotStatus.BUSY);
        var order = inOrder(robots, entityManager, missions);
        order.verify(robots).lockFleet();
        order.verify(entityManager).refresh(robot);
        order.verify(missions).findByOrderId(orderId);
        order.verify(missions).save(assigned);
    }

    @Test
    void noEligibleRobotDoesNotWrite() {
        robot.setBattery(19);
        assertThat(facade.assignMission(orderId, 0, 0)).isEmpty();
        assertThat(robot.getStatus()).isEqualTo(RobotStatus.IDLE);
        verify(missions, never()).save(any());
    }

    @Test
    void duplicateReturnsExistingEvenWhenNoRobotIsEligible() {
        Mission existing = new Mission(UUID.randomUUID(), orderId, robot, MissionStatus.ASSIGNED);
        robot.setStatus(RobotStatus.BUSY);
        when(missions.findByOrderId(orderId)).thenReturn(List.of(
                new Mission(UUID.randomUUID(), orderId, robot, MissionStatus.ABORTED), existing));
        assertThat(facade.assignMission(orderId, 0, 0)).containsSame(existing);
        verify(missions, never()).save(any());
    }

    @ParameterizedTest
    @EnumSource(value = MissionStatus.class, names = {"COMPLETED", "ABORTED"})
    void terminalMissionRequiresExplicitReassignment(MissionStatus status) {
        when(missions.findByOrderId(orderId)).thenReturn(List.of(new Mission(UUID.randomUUID(), orderId, robot, status)));
        assertThatThrownBy(() -> facade.assignMission(orderId, 0, 0)).isInstanceOf(IllegalStateException.class);
        assertThat(robot.getStatus()).isEqualTo(RobotStatus.IDLE);
        verify(missions, never()).save(any());
    }

    @Test
    void invalidInputFailsBeforeDatabaseAccess() {
        assertThatThrownBy(() -> facade.assignMission(null, 0, 0)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> facade.assignMission(orderId, -1, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> facade.assignMission(orderId, 0, -1)).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(robots, missions, entityManager);
    }
}
