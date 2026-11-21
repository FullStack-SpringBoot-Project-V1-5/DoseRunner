package tn.rnu.eniso.doserunner.dispatch;

import org.junit.jupiter.api.Test;
import tn.rnu.eniso.doserunner.dispatch.dao.Robot;
import tn.rnu.eniso.doserunner.dispatch.dao.RobotStatus;

import static org.assertj.core.api.Assertions.*;

class RobotTest {
    @Test
    void invalidUpdatesDoNotChangeRobotState() {
        Robot robot = new Robot("TEST-01", 2, 3, 50, RobotStatus.IDLE);
        assertThatThrownBy(() -> robot.setPosition(-1, 4)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> robot.setPosition(4, -1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> robot.setBattery(-1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> robot.setBattery(101)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> robot.setStatus(null)).isInstanceOf(NullPointerException.class);
        assertThat(robot.getX()).isEqualTo(2);
        assertThat(robot.getY()).isEqualTo(3);
        assertThat(robot.getBattery()).isEqualTo(50);
        assertThat(robot.getStatus()).isEqualTo(RobotStatus.IDLE);
    }

    @Test
    void rejectsInvalidFleetIds() {
        for (String id : new String[] {null, "", " ", "x".repeat(65)}) {
            assertThatThrownBy(() -> new Robot(id, 0, 0, 100, RobotStatus.IDLE))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }
}
