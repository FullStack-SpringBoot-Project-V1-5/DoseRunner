package tn.rnu.eniso.doserunner.dispatch;

import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import tn.rnu.eniso.doserunner.contracts.robot.RobotTelemetry;
import tn.rnu.eniso.doserunner.dispatch.dao.RobotRepository;
import tn.rnu.eniso.doserunner.dispatch.facade.RobotTelemetryFacade;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class RobotTelemetryFacadeTest {
    static Stream<Arguments> invalid() {
        return Stream.of(
                Arguments.of(null, 0, 0, 100), Arguments.of(" ", 0, 0, 100),
                Arguments.of("x".repeat(65), 0, 0, 100), Arguments.of("R-01", -1, 0, 100),
                Arguments.of("R-01", 0, -1, 100), Arguments.of("R-01", 0, 0, -1),
                Arguments.of("R-01", 0, 0, 101));
    }

    @ParameterizedTest @MethodSource("invalid")
    void rejectsInvalidReadingsBeforeDatabaseAccess(String id, int x, int y, int battery) {
        var robots = mock(RobotRepository.class);
        var facade = new RobotTelemetryFacade(robots);
        assertThatThrownBy(() -> facade.update(new RobotTelemetry(id, x, y, battery)))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(robots);
    }
}
