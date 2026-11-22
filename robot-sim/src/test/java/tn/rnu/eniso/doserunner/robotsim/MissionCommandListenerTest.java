package tn.rnu.eniso.doserunner.robotsim;

import jakarta.jms.*;
import org.junit.jupiter.api.Test;
import tn.rnu.eniso.doserunner.contracts.messaging.*;
import tn.rnu.eniso.doserunner.contracts.robot.MissionCommand;
import tn.rnu.eniso.doserunner.robotsim.facade.SimulationFleet;
import tn.rnu.eniso.doserunner.robotsim.pres.MissionCommandListener;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class MissionCommandListenerTest {
    final AvroJmsMessages adapter=mock(AvroJmsMessages.class);
    final SimulationFleet fleet=mock(SimulationFleet.class);
    final MissionCommandListener listener=new MissionCommandListener(adapter,fleet);
    @Test void preservesTraceAndCorrelationAndValidatesMetadata() throws Exception {
        var c=SimulationFleetTest.command("R-01",1); var m=mock(BytesMessage.class);
        when(adapter.fromMessage(m,MissionCommand.class)).thenReturn(c);
        when(m.getStringProperty(MessageHeaders.EVENT_TYPE)).thenReturn("MissionCommand");
        when(m.getStringProperty(MessageHeaders.ROBOT_ID)).thenReturn("R-01");
        when(m.getStringProperty(MessageHeaders.TRACE_ID)).thenReturn("trace");
        when(m.getJMSCorrelationID()).thenReturn("correlation");
        listener.onMessage(m); verify(fleet).accept(c,"trace","correlation");
        for(String field : new String[]{MessageHeaders.ROBOT_ID,MessageHeaders.ORDER_ID,MessageHeaders.GROUP_ID,MessageHeaders.EVENT_TYPE}) {
            when(m.getStringProperty(field)).thenReturn("wrong");
            assertThatThrownBy(() -> listener.onMessage(m)).isInstanceOf(IllegalArgumentException.class);
            when(m.getStringProperty(field)).thenReturn(switch(field) {
                case "robotId", "JMSXGroupID" -> "R-01";
                case "eventType" -> "MissionCommand";
                default -> c.getOrderId();
            });
        }
        verifyNoMoreInteractions(fleet);
    }
    @Test void sharedAdapterRejectsTextAndMalformedAvro() throws Exception {
        var real=new AvroJmsMessages(AvroMessageCodec.forContracts());
        assertThatThrownBy(() -> real.fromMessage(mock(TextMessage.class),MissionCommand.class)).isInstanceOf(RuntimeException.class);
        var bytes=mock(BytesMessage.class); when(bytes.getBodyLength()).thenReturn(2L);
        assertThatThrownBy(() -> real.fromMessage(bytes,MissionCommand.class)).isInstanceOf(RuntimeException.class);
    }
}
