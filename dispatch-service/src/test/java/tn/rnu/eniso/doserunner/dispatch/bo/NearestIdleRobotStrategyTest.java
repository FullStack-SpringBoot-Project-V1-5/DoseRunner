package tn.rnu.eniso.doserunner.dispatch.bo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.ServiceLoader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import tn.rnu.eniso.doserunner.dispatch.dao.RobotStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NearestIdleRobotStrategyTest {
    private final DispatchStrategy strategy = new NearestIdleRobotStrategy();

    @Test
    void serviceLoaderDiscoversAndExecutesV1WithoutSpring() {
        List<DispatchStrategy> providers = ServiceLoader.load(DispatchStrategy.class).stream()
                .map(ServiceLoader.Provider::get).toList();
        assertThat(providers).extracting(Object::getClass).containsExactly(NearestIdleRobotStrategy.class);
        RobotCandidate robot = robot("R-01", 0, 0, 20, RobotStatus.IDLE);
        assertThat(providers.getFirst().select(List.of(robot), 0, 0)).contains(robot);
    }

    @Test
    void usesManhattanRatherThanEuclideanDistance() {
        RobotCandidate manhattanNearest = robot("R-02", 3, 0, 20, RobotStatus.IDLE);
        RobotCandidate euclideanNearest = robot("R-01", 2, 2, 100, RobotStatus.IDLE);
        assertThat(strategy.select(List.of(euclideanNearest, manhattanNearest), 0, 0))
                .contains(manhattanNearest);
    }

    @Test
    void measuresFromSuppliedPickupPointRatherThanOrigin() {
        RobotCandidate nearOrigin = robot("R-01", 0, 1, 100, RobotStatus.IDLE);
        RobotCandidate nearPickup = robot("R-02", 9, 10, 20, RobotStatus.IDLE);
        assertThat(strategy.select(List.of(nearOrigin, nearPickup), 10, 10)).contains(nearPickup);
    }

    @Test
    void robotAlreadyAtPickupWins() {
        RobotCandidate atPickup = robot("R-02", 5, 7, 20, RobotStatus.IDLE);
        assertThat(strategy.select(List.of(robot("R-01", 5, 6, 100, RobotStatus.IDLE), atPickup), 5, 7))
                .contains(atPickup);
    }

    @ParameterizedTest
    @CsvSource({"0,false", "19,false", "20,true", "21,true", "100,true"})
    void batteryThresholdIsInclusive(int battery, boolean eligible) {
        RobotCandidate candidate = robot("R-01", 0, 0, battery, RobotStatus.IDLE);
        assertThat(strategy.select(List.of(candidate), 0, 0).isPresent()).isEqualTo(eligible);
    }

    @ParameterizedTest
    @EnumSource(value = RobotStatus.class, names = {"BUSY", "OFFLINE"})
    void excludesNonIdleRobotsEvenWhenCloser(RobotStatus status) {
        RobotCandidate eligible = robot("R-02", 5, 5, 20, RobotStatus.IDLE);
        assertThat(strategy.select(List.of(robot("R-01", 0, 0, 100, status), eligible), 0, 0))
                .contains(eligible);
    }

    @Test
    void excludesCloserLowBatteryRobot() {
        RobotCandidate eligible = robot("R-02", 5, 5, 20, RobotStatus.IDLE);
        assertThat(strategy.select(List.of(robot("R-01", 0, 0, 19, RobotStatus.IDLE), eligible), 0, 0))
                .contains(eligible);
    }

    @Test
    void returnsEmptyForEmptyFleetOrAllIneligible() {
        assertThat(strategy.select(List.of(), 0, 0)).isEmpty();
        assertThat(strategy.select(List.of(
                robot("R-01", 0, 0, 19, RobotStatus.IDLE),
                robot("R-02", 0, 0, 100, RobotStatus.BUSY),
                robot("R-03", 0, 0, 100, RobotStatus.OFFLINE)), 0, 0)).isEmpty();
    }

    @Test
    void tiesUseLexicographicIdNotBatteryOrInputOrder() {
        RobotCandidate winner = robot("R-10", 1, 0, 20, RobotStatus.IDLE);
        RobotCandidate other = robot("R-2", 0, 1, 100, RobotStatus.IDLE);
        assertThat(strategy.select(List.of(other, winner), 0, 0)).contains(winner);
        assertThat(strategy.select(List.of(winner, other), 0, 0)).contains(winner);
    }

    @Test
    void handlesDistanceBeyondIntegerRangeAndNegativeDeltas() {
        int max = Integer.MAX_VALUE;
        RobotCandidate far = robot("R-01", max, max, 100, RobotStatus.IDLE);
        RobotCandidate nearer = robot("R-02", max, 0, 20, RobotStatus.IDLE);
        assertThat(strategy.select(List.of(far, nearer), 0, 0)).contains(nearer);
        RobotCandidate origin = robot("R-01", 0, 0, 100, RobotStatus.IDLE);
        RobotCandidate nearPickup = robot("R-02", max - 1, max, 20, RobotStatus.IDLE);
        assertThat(strategy.select(List.of(origin, nearPickup), max, max)).contains(nearPickup);
    }

    @Test
    void doesNotMutateInputAndRetainsNoSelectionBetweenCalls() {
        RobotCandidate far = robot("R-01", 10, 10, 100, RobotStatus.IDLE);
        RobotCandidate near = robot("R-02", 0, 0, 20, RobotStatus.IDLE);
        List<RobotCandidate> fleet = new ArrayList<>(List.of(far, near));
        assertThat(strategy.select(Collections.unmodifiableList(fleet), 0, 0)).contains(near);
        assertThat(fleet).containsExactly(far, near);
        assertThat(near).isEqualTo(robot("R-02", 0, 0, 20, RobotStatus.IDLE));
        assertThat(strategy.select(List.of(far), 0, 0)).contains(far);
        assertThat(strategy.select(List.of(), 0, 0)).isEmpty();
    }

    @Test
    void rejectsNullFleetOrNullEntries() {
        assertThatThrownBy(() -> strategy.select(null, 0, 0)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> strategy.select(Collections.singletonList(null), 0, 0))
                .isInstanceOf(NullPointerException.class);
    }

    @ParameterizedTest
    @CsvSource({"-1,0", "0,-1"})
    void rejectsNegativePickupEvenForEmptyFleet(int x, int y) {
        assertThatThrownBy(() -> strategy.select(List.of(), x, y)).isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t"})
    void rejectsInvalidCandidateId(String id) {
        assertThatThrownBy(() -> robot(id, 0, 0, 20, RobotStatus.IDLE))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsOverlongCandidateId() {
        assertThatThrownBy(() -> robot("x".repeat(65), 0, 0, 20, RobotStatus.IDLE))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @CsvSource({"-1,0,20", "0,-1,20", "0,0,-1", "0,0,101"})
    void rejectsOutOfRangeCandidateValues(int x, int y, int battery) {
        assertThatThrownBy(() -> robot("R-01", x, y, battery, RobotStatus.IDLE))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsMissingCandidateStatus() {
        assertThatThrownBy(() -> robot("R-01", 0, 0, 20, null)).isInstanceOf(NullPointerException.class);
    }

    private static RobotCandidate robot(String id, int x, int y, int battery, RobotStatus status) {
        return new RobotCandidate(id, x, y, battery, status);
    }
}
