package tn.rnu.eniso.doserunner.dispatch;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tn.rnu.eniso.doserunner.dispatch.dao.*;
import tn.rnu.eniso.doserunner.dispatch.facade.DispatchFacade;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = "spring.datasource.hikari.pool-name=C003AssignmentPool")
@DirtiesContext
class DispatchAssignmentTest extends PostgresTestSupport {
    @Autowired DispatchFacade facade;
    @Autowired MissionRepository missions;
    @Autowired RobotRepository robots;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactionManager;

    @BeforeEach
    void resetIsolatedDatabase() {
        missions.deleteAllInBatch();
        jdbc.update("UPDATE robots SET status = 'IDLE', battery = 100");
    }

    @Test
    void assignsNearestRobotAndPersistsBothStates() {
        UUID order = UUID.randomUUID();
        Mission mission = facade.assignMission(order, 5, 0).orElseThrow();
        assertThat(missions.findById(mission.getId()).orElseThrow().getOrderId()).isEqualTo(order);
        assertThat(mission.getRobot().getId()).isEqualTo("R-02");
        assertThat(mission.getStatus()).isEqualTo(MissionStatus.ASSIGNED);
        assertThat(robots.findById("R-02").orElseThrow().getStatus()).isEqualTo(RobotStatus.BUSY);
        assertThat(missions.count()).isOne();
    }

    @Test
    void unavailableFleetIsUnchangedAndLaterRequestCanRetry() {
        UUID order = UUID.randomUUID();
        jdbc.update("UPDATE robots SET battery = 19");
        assertThat(facade.assignMission(order, 0, 0)).isEmpty();
        assertThat(missions.count()).isZero();
        assertThat(robots.findAll()).allSatisfy(robot -> {
            assertThat(robot.getStatus()).isEqualTo(RobotStatus.IDLE);
            assertThat(robot.getBattery()).isEqualTo(19);
        });
        jdbc.update("UPDATE robots SET battery = 20 WHERE id = 'R-01'");
        assertThat(facade.assignMission(order, 0, 0)).isPresent();
    }

    @Test
    void refreshesRobotsAlreadyLoadedInTheCallingTransaction() {
        new TransactionTemplate(transactionManager).executeWithoutResult(transaction -> {
            assertThat(robots.findById("R-01").orElseThrow().getStatus()).isEqualTo(RobotStatus.IDLE);
            jdbc.update("UPDATE robots SET status = 'OFFLINE' WHERE id = 'R-01'");
            assertThat(facade.assignMission(UUID.randomUUID(), 0, 0).orElseThrow().getRobot().getId())
                    .isEqualTo("R-02");
        });
        assertThat(robots.findById("R-01").orElseThrow().getStatus()).isEqualTo(RobotStatus.OFFLINE);
    }

    @Test
    void repeatedOrderReturnsSameMissionWithoutAnotherRobotUpdate() {
        UUID order = UUID.randomUUID();
        UUID first = facade.assignMission(order, 0, 0).orElseThrow().getId();
        assertThat(facade.assignMission(order, 5, 0).orElseThrow().getId()).isEqualTo(first);
        assertThat(missions.count()).isOne();
        assertThat(robots.findAll()).filteredOn(robot -> robot.getStatus() == RobotStatus.BUSY).hasSize(1);
    }

    @ParameterizedTest
    @EnumSource(value = MissionStatus.class, names = {"COMPLETED", "ABORTED"})
    void terminalHistoryRejectsImplicitReassignment(MissionStatus status) {
        UUID order = UUID.randomUUID();
        missions.saveAndFlush(new Mission(UUID.randomUUID(), order, robots.findById("R-01").orElseThrow(), status));
        assertThatThrownBy(() -> facade.assignMission(order, 0, 0)).isInstanceOf(IllegalStateException.class);
        assertThat(missions.count()).isOne();
        assertThat(robots.findAll()).allMatch(robot -> robot.getStatus() == RobotStatus.IDLE);
    }

    @Test
    void commitFailureRollsBackMissionAndRobotUpdate() {
        jdbc.execute("CREATE FUNCTION reject_assignment() RETURNS trigger LANGUAGE plpgsql AS $$ "
                + "BEGIN IF (SELECT status FROM robots WHERE id = NEW.robot_id) <> 'BUSY' THEN "
                + "RAISE EXCEPTION 'robot update was not flushed'; END IF; "
                + "RAISE EXCEPTION 'synthetic commit failure'; END $$");
        jdbc.execute("CREATE CONSTRAINT TRIGGER reject_assignment AFTER INSERT ON missions "
                + "DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION reject_assignment()");
        try {
            assertThatThrownBy(() -> facade.assignMission(UUID.randomUUID(), 0, 0))
                    .isInstanceOf(RuntimeException.class).hasStackTraceContaining("synthetic commit failure");
            assertThat(missions.count()).isZero();
            assertThat(robots.findAll()).allMatch(robot -> robot.getStatus() == RobotStatus.IDLE);
        } finally {
            jdbc.execute("DROP TRIGGER reject_assignment ON missions");
            jdbc.execute("DROP FUNCTION reject_assignment()");
        }
        assertThat(facade.assignMission(UUID.randomUUID(), 0, 0)).isPresent();
    }

    @Test
    void concurrentDifferentOrdersUseDifferentRobotsAfterWaitingForCommit() throws Exception {
        var results = concurrentAssignments(UUID.randomUUID(), UUID.randomUUID());
        assertThat(results.first.orElseThrow().getRobot().getId()).isEqualTo("R-01");
        assertThat(results.second.orElseThrow().getRobot().getId()).isEqualTo("R-02");
        assertThat(missions.count()).isEqualTo(2);
    }

    @Test
    void concurrentDifferentOrdersCannotShareTheLastEligibleRobot() throws Exception {
        jdbc.update("UPDATE robots SET status = 'OFFLINE' WHERE id <> 'R-01'");
        var results = concurrentAssignments(UUID.randomUUID(), UUID.randomUUID());
        assertThat(results.first).isPresent();
        assertThat(results.second).isEmpty();
        assertThat(missions.count()).isOne();
    }

    @Test
    void concurrentDuplicatesReturnSameMissionAfterWaitingForCommit() throws Exception {
        UUID order = UUID.randomUUID();
        var results = concurrentAssignments(order, order);
        assertThat(results.second.orElseThrow().getId()).isEqualTo(results.first.orElseThrow().getId());
        assertThat(missions.count()).isOne();
        assertThat(robots.findAll()).filteredOn(robot -> robot.getStatus() == RobotStatus.BUSY).hasSize(1);
    }

    private record Results(Optional<Mission> first, Optional<Mission> second) { }

    private Results concurrentAssignments(UUID firstOrder, UUID secondOrder) throws Exception {
        var assigned = new CountDownLatch(1);
        var commit = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            Future<Optional<Mission>> first = executor.submit(() ->
                    new TransactionTemplate(transactionManager).execute(transaction -> {
                        var result = facade.assignMission(firstOrder, 0, 0);
                        assigned.countDown();
                        await(commit);
                        return result;
                    }));
            Future<Optional<Mission>> second = null;
            try {
                assertThat(assigned.await(10, TimeUnit.SECONDS)).isTrue();
                second = executor.submit(() -> facade.assignMission(secondOrder, 0, 0));
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
                boolean blocked = false;
                while (System.nanoTime() < deadline) {
                    blocked = jdbc.queryForObject("SELECT count(*) > 0 FROM pg_stat_activity "
                            + "WHERE datname = current_database() AND wait_event_type = 'Lock' "
                            + "AND query LIKE '%robots%'", Boolean.class);
                    if (blocked) break;
                    Thread.sleep(20);
                }
                assertThat(blocked).as("second transaction waits on PostgreSQL robot row locks").isTrue();
                assertThat(second.isDone()).isFalse();
            } finally {
                commit.countDown();
            }
            return new Results(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS));
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(15, TimeUnit.SECONDS)) throw new AssertionError("Timed out waiting for test transaction");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError(e);
        }
    }
}
