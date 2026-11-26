package tn.rnu.eniso.doserunner.dispatch;

import jakarta.jms.Message;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import tn.rnu.eniso.doserunner.contracts.common.*;
import tn.rnu.eniso.doserunner.contracts.events.OrderValidated;
import tn.rnu.eniso.doserunner.contracts.messaging.*;
import tn.rnu.eniso.doserunner.dispatch.facade.OrderValidatedFacade;
import tn.rnu.eniso.doserunner.dispatch.pres.OrderValidatedListener;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class OrderValidatedListenerTest {
    final AvroJmsMessages messages=mock(AvroJmsMessages.class);
    final OrderValidatedFacade facade=mock(OrderValidatedFacade.class);
    final OrderValidatedListener listener=new OrderValidatedListener(messages,facade);
    @AfterEach void cleanup() { MDC.clear(); }
    static OrderValidated event() {
        return OrderValidated.newBuilder().setEventId(UUID.randomUUID().toString()).setOccurredAt(Instant.now())
                .setOrderId(UUID.randomUUID().toString()).setPharmacistId("demo-pharmacist")
                .setDestination(new Location("DEMO",null,new GridPoint(2,3))).setControlled(true).build();
    }
    @Test void preservesTraceAndRestoresContextAfterSuccessAndFailure() throws Exception {
        var event=event(); var message=mock(Message.class);
        when(messages.fromMessage(message,OrderValidated.class)).thenReturn(event);
        when(message.getStringProperty(MessageHeaders.TRACE_ID)).thenReturn("trace-test");
        MDC.setContextMap(Map.of("original","value"));
        listener.onMessage(message);
        verify(facade).process(event,"trace-test");
        assertThat(MDC.getCopyOfContextMap()).containsExactlyEntriesOf(Map.of("original","value"));
        doThrow(new IllegalStateException("synthetic failure")).when(facade).process(event,"trace-test");
        assertThatThrownBy(() -> listener.onMessage(message)).isInstanceOf(IllegalStateException.class);
        assertThat(MDC.getCopyOfContextMap()).containsExactlyEntriesOf(Map.of("original","value"));
    }
    @Test void generatesTraceAndRejectsConflictingOrderMetadata() throws Exception {
        var event=event(); var message=mock(Message.class);
        when(messages.fromMessage(message,OrderValidated.class)).thenReturn(event);
        doAnswer(call -> { assertThat(UUID.fromString(call.getArgument(1))).isNotNull(); return null; })
                .when(facade).process(eq(event),anyString());
        listener.onMessage(message);
        assertThat(MDC.getCopyOfContextMap()).isNullOrEmpty();
        when(message.getStringProperty(MessageHeaders.ORDER_ID)).thenReturn(UUID.randomUUID().toString());
        assertThatThrownBy(() -> listener.onMessage(message)).isInstanceOf(IllegalArgumentException.class);
        verify(facade,times(1)).process(eq(event),anyString());
    }
}
