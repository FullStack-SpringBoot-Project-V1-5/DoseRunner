package tn.rnu.eniso.doserunner.robotlink;

import jakarta.jms.*;
import java.io.IOException;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.avro.Schema;
import org.apache.avro.SchemaNormalization;
import tn.rnu.eniso.doserunner.contracts.common.GridPoint;
import tn.rnu.eniso.doserunner.contracts.robot.MissionCommand;
import tn.rnu.eniso.doserunner.contracts.robot.RobotTelemetry;
import static org.junit.jupiter.api.Assertions.*;

class RobotLinkTest {
    @ParameterizedTest @NullAndEmptySource @ValueSource(strings={"R' OR '1'='1", "R 01", "R/01"})
    void invalidRobotIdFailsBeforeConnecting(String id) {
        assertThrows(IllegalArgumentException.class, () -> new JmsRobotLink(null, id, "unused", "unused"));
    }

    @Test void failedRollbackClosesConnectionAndPreventsLaterAcceptance() throws Exception {
        AtomicInteger sessions = new AtomicInteger(), closes = new AtomicInteger();
        TextMessage malformed = stub(TextMessage.class, (p,m,a) -> null);
        MessageConsumer consumer = stub(MessageConsumer.class, (p,m,a) -> malformed);
        Session commands = stub(Session.class, (p,m,a) -> {
            if (m.getName().equals("createConsumer")) return consumer;
            if (m.getName().equals("rollback")) throw new JMSException("rollback failed");
            return null;
        });
        Session telemetry = stub(Session.class, (p,m,a) -> m.getName().equals("createProducer")
                ? stub(MessageProducer.class, (x,y,z) -> null) : null);
        Connection connection = stub(Connection.class, (p,m,a) -> {
            if (m.getName().equals("createSession")) return sessions.getAndIncrement() == 0 ? commands : telemetry;
            if (m.getName().equals("close")) closes.incrementAndGet();
            return null;
        });
        ConnectionFactory factory = stub(ConnectionFactory.class, (p,m,a) -> connection);
        RobotLink link = new JmsRobotLink(factory, "R-01", "unused", "unused");
        IOException error = assertThrows(IOException.class, () -> link.receive(1));
        assertEquals("rollback failed", error.getSuppressed()[0].getMessage());
        assertEquals(1, closes.get());
        assertThrows(java.lang.IllegalStateException.class, () -> link.receive(1));
    }

    private static <T> T stub(Class<T> type, InvocationHandler handler) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler));
    }

    @Test void generatedSchemasMatchFrozenFiles() throws Exception {
        Path schemas = Path.of("../../../contracts/src/main/avro");
        Schema.Parser parser = new Schema.Parser();
        Schema point = parser.parse(Files.readString(schemas.resolve("common/GridPoint.avsc")));
        Schema command = parser.parse(Files.readString(schemas.resolve("robot/MissionCommand.avsc")));
        Schema telemetry = parser.parse(Files.readString(schemas.resolve("robot/RobotTelemetry.avsc")));
        assertEquals(SchemaNormalization.parsingFingerprint64(point), SchemaNormalization.parsingFingerprint64(GridPoint.getClassSchema()));
        assertEquals(SchemaNormalization.parsingFingerprint64(command), SchemaNormalization.parsingFingerprint64(MissionCommand.getClassSchema()));
        assertEquals(SchemaNormalization.parsingFingerprint64(telemetry), SchemaNormalization.parsingFingerprint64(RobotTelemetry.getClassSchema()));
        assertEquals(4, RobotTelemetry.getClassSchema().getFields().size());
    }
}
