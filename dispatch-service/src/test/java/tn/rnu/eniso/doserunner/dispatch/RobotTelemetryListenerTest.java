package tn.rnu.eniso.doserunner.dispatch;

import jakarta.jms.Message;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.slf4j.MDC;
import tn.rnu.eniso.doserunner.commons.trace.TraceHeaders;
import tn.rnu.eniso.doserunner.contracts.messaging.AvroJmsMessages;
import tn.rnu.eniso.doserunner.contracts.messaging.MessageHeaders;
import tn.rnu.eniso.doserunner.contracts.robot.RobotTelemetry;
import tn.rnu.eniso.doserunner.dispatch.facade.RobotTelemetryFacade;
import tn.rnu.eniso.doserunner.dispatch.pres.RobotTelemetryListener;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class RobotTelemetryListenerTest {
    final AvroJmsMessages messages = mock(AvroJmsMessages.class);
    final RobotTelemetryFacade facade = mock(RobotTelemetryFacade.class);
    final RobotTelemetryListener listener = new RobotTelemetryListener(messages, facade);
    final RobotTelemetry telemetry = new RobotTelemetry("R-01", 1, 2, 99);

    @AfterEach void cleanup() { MDC.clear(); }

    @Test void propagatesTraceAndRestoresContextOnSuccessAndFailure() throws Exception {
        var message = mock(Message.class);
        when(messages.fromMessage(message, RobotTelemetry.class)).thenReturn(telemetry);
        when(message.getStringProperty(MessageHeaders.TRACE_ID)).thenReturn("telemetry-trace");
        MDC.setContextMap(Map.of("original", "value"));
        doAnswer(call -> {
            assertThat(MDC.get(TraceHeaders.MDC_TRACE_ID)).isEqualTo("telemetry-trace");
            return null;
        }).when(facade).update(telemetry);
        listener.onMessage(message);
        verify(facade).update(telemetry);
        assertThat(MDC.getCopyOfContextMap()).containsExactlyEntriesOf(Map.of("original", "value"));
        doThrow(new IllegalStateException("database unavailable")).when(facade).update(telemetry);
        assertThatThrownBy(() -> listener.onMessage(message)).isInstanceOf(IllegalStateException.class);
        assertThat(MDC.getCopyOfContextMap()).containsExactlyEntriesOf(Map.of("original", "value"));
    }

    @ParameterizedTest
    @CsvSource({"R-02,RobotTelemetry", "R-01,MissionCommand"})
    void rejectsConflictingMetadataBeforeWriting(String robotId, String type) throws Exception {
        var message = mock(Message.class);
        when(messages.fromMessage(message, RobotTelemetry.class)).thenReturn(telemetry);
        when(message.getStringProperty(MessageHeaders.ROBOT_ID)).thenReturn(robotId);
        when(message.getStringProperty(MessageHeaders.EVENT_TYPE)).thenReturn(type);
        assertThatThrownBy(() -> listener.onMessage(message)).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(facade);
        assertThat(MDC.getCopyOfContextMap()).isNullOrEmpty();
    }
}
