package tn.rnu.eniso.doserunner.dispatch;

import jakarta.persistence.EntityManager;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import tn.rnu.eniso.doserunner.dispatch.dao.*;

import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class FleetPersistenceTest extends PostgresTestSupport {
    @Autowired RobotRepository robots;
    @Autowired MissionRepository missions;
    @Autowired EntityManager entityManager;
    @Autowired JdbcTemplate jdbc;
    @Autowired Flyway flyway;

    @Test
    void freshMigrationSeedsExactlyThreeRobotsAndDoesNotReseedOnRestart() {
        assertThat(flyway.info().current().getVersion().toString()).isEqualTo("1");
        assertThat(robots.findAll()).extracting(Robot::getId)
                .containsExactlyInAnyOrder("R-01", "R-02", "R-03");
        assertThat(robots.findAll()).allSatisfy(robot -> {
            assertThat(robot.getStatus()).isEqualTo(RobotStatus.IDLE);
            assertThat(robot.getBattery()).isBetween(0, 100);
        });
        assertThat(missions.count()).isZero();
        assertThat(flyway.migrate().migrationsExecuted).isZero();
        assertThat(robots.count()).isEqualTo(3);
    }

    @Test
    @Transactional
    void robotAndMissionRoundTripAndUpdateAfterClearingPersistenceContext() {
        Robot robot = robots.saveAndFlush(new Robot("TEST-01", 3, 4, 0, RobotStatus.IDLE));
        UUID missionId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        missions.saveAndFlush(new Mission(missionId, orderId, robot, MissionStatus.ASSIGNED));
        entityManager.clear();

        Mission loaded = missions.findById(missionId).orElseThrow();
        assertThat(loaded.getOrderId()).isEqualTo(orderId);
        assertThat(loaded.getStatus()).isEqualTo(MissionStatus.ASSIGNED);
        Robot loadedRobot = loaded.getRobot();
        assertThat(loadedRobot.getId()).isEqualTo("TEST-01");
        assertThat(loadedRobot.getX()).isEqualTo(3);
        assertThat(loadedRobot.getY()).isEqualTo(4);
        assertThat(loadedRobot.getBattery()).isZero();
        loadedRobot.setPosition(6, 7);
        loadedRobot.setBattery(100);
        loadedRobot.setStatus(RobotStatus.BUSY);
        loaded.setStatus(MissionStatus.COMPLETED);
        entityManager.flush();
        entityManager.clear();

        Robot updated = robots.findById("TEST-01").orElseThrow();
        assertThat(updated.getX()).isEqualTo(6);
        assertThat(updated.getY()).isEqualTo(7);
        assertThat(updated.getBattery()).isEqualTo(100);
        assertThat(updated.getStatus()).isEqualTo(RobotStatus.BUSY);
        assertThat(missions.findById(missionId).orElseThrow().getStatus()).isEqualTo(MissionStatus.COMPLETED);
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "INSERT INTO robots VALUES ('BAD', -1, 0, 50, 'IDLE')",
        "INSERT INTO robots VALUES ('BAD', 0, -1, 50, 'IDLE')",
        "INSERT INTO robots VALUES ('BAD', 0, 0, -1, 'IDLE')",
        "INSERT INTO robots VALUES ('BAD', 0, 0, 101, 'IDLE')",
        "INSERT INTO robots VALUES ('BAD', 0, 0, 50, 'UNKNOWN')",
        "INSERT INTO robots VALUES (' ', 0, 0, 50, 'IDLE')",
        "INSERT INTO missions VALUES (gen_random_uuid(), gen_random_uuid(), 'MISSING', 'ASSIGNED')",
        "INSERT INTO missions VALUES (gen_random_uuid(), gen_random_uuid(), 'R-01', 'UNKNOWN')",
        "INSERT INTO missions VALUES (gen_random_uuid(), NULL, 'R-01', 'ASSIGNED')",
        "DELETE FROM robots WHERE id = 'R-01'"
    })
    @Transactional
    void databaseRejectsInvalidStateAndDeletingAReferencedRobot(String sql) {
        jdbc.update("INSERT INTO missions VALUES (gen_random_uuid(), gen_random_uuid(), 'R-01', 'ASSIGNED')");
        assertThatThrownBy(() -> jdbc.update(sql)).isInstanceOf(DataIntegrityViolationException.class);
    }
}
