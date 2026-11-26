package tn.rnu.eniso.doserunner.robotsim;

import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tn.rnu.eniso.doserunner.contracts.common.GridPoint;
import tn.rnu.eniso.doserunner.contracts.messaging.AvroMessageCodec;
import tn.rnu.eniso.doserunner.contracts.robot.MissionCommand;
import tn.rnu.eniso.doserunner.contracts.robot.RobotTelemetry;
import static org.assertj.core.api.Assertions.assertThat;

/** RobotLink uses generated APIs because the shared adapter's source/runtime requires Java 25. */
class RobotLinkEncodingTest {
    final AvroMessageCodec codec = AvroMessageCodec.forContracts();

    @Test void generatedDecoderReadsDispatchCodecCommand() throws Exception {
        var command = new MissionCommand(UUID.randomUUID().toString(), "R-01", UUID.randomUUID().toString(), 1,
                List.of(new GridPoint(1,0)), "C1", Instant.ofEpochMilli(1234));
        assertThat(MissionCommand.fromByteBuffer(ByteBuffer.wrap(codec.encode(command)))).isEqualTo(command);
    }

    @Test void sharedCodecReadsGeneratedTelemetryEncoder() throws Exception {
        var telemetry = new RobotTelemetry("R-01", 1, 0, 99);
        var encoded = telemetry.toByteBuffer();
        byte[] body = new byte[encoded.remaining()];
        encoded.get(body);
        assertThat(codec.decode(body, RobotTelemetry.class)).isEqualTo(telemetry);
        assertThat(body).isEqualTo(codec.encode(telemetry));
    }
}
