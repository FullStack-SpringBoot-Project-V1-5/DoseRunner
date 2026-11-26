package tn.rnu.eniso.doserunner.dispatch;

import jakarta.jms.*;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.jms.core.SessionCallback;
import tn.rnu.eniso.doserunner.contracts.events.RobotAssigned;
import tn.rnu.eniso.doserunner.contracts.robot.MissionCommand;
import tn.rnu.eniso.doserunner.contracts.messaging.*;
import tn.rnu.eniso.doserunner.dispatch.dao.DispatchMessageStore.Publication;
import tn.rnu.eniso.doserunner.dispatch.messaging.DispatchEventPublisher;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class DispatchEventPublisherTest {
    @Test void failureOnSecondSendRollsBackTheWholeJmsTransaction() throws Exception {
        var codec=AvroMessageCodec.forContracts();
        var template=mock(JmsTemplate.class); var session=mock(Session.class);
        var topic=mock(Topic.class); var queue=mock(Queue.class);
        var first=mock(MessageProducer.class); var second=mock(MessageProducer.class);
        when(session.createTopic(Addresses.EVENTS)).thenReturn(topic);
        when(session.createQueue(Addresses.ROBOT_COMMANDS)).thenReturn(queue);
        when(session.createProducer(topic)).thenReturn(first); when(session.createProducer(queue)).thenReturn(second);
        when(session.createBytesMessage()).thenAnswer(call -> mock(BytesMessage.class));
        doThrow(new JMSException("synthetic second-send failure")).when(second).send(any(Message.class));
        doAnswer(call -> ((SessionCallback<?>)call.getArgument(0)).doInJms(session)).when(template).execute(any(SessionCallback.class));
        UUID mission=UUID.randomUUID(),order=UUID.randomUUID();
        var assigned=RobotAssigned.newBuilder().setEventId(UUID.randomUUID().toString()).setOccurredAt(Instant.now())
                .setOrderId(order.toString()).setRobotId("R-01").setMissionId(mission.toString()).setEtaSeconds(0).setPath(List.of()).build();
        var command=MissionCommand.newBuilder().setMissionId(mission.toString()).setOrderId(order.toString()).setRobotId("R-01")
                .setSeq(1).setWaypoints(List.of()).setCompartmentCode("C1").setIssuedAt(Instant.now()).build();
        var publication=new Publication(1,mission,order,"R-01","trace",codec.encode(assigned),codec.encode(command));
        assertThatThrownBy(() -> new DispatchEventPublisher(template,codec,new AvroJmsMessages(codec)).publish(publication))
                .hasMessageContaining("synthetic second-send failure");
        verify(first).send(any(Message.class)); verify(session).rollback(); verify(session,never()).commit();
    }
}
