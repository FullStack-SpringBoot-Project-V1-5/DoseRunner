package tn.rnu.eniso.doserunner.contracts.messaging;

import jakarta.jms.BytesMessage;
import jakarta.jms.Session;
import jakarta.jms.TextMessage;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tn.rnu.eniso.doserunner.contracts.AvroCodecException;
import tn.rnu.eniso.doserunner.contracts.events.OrderValidated;
import tn.rnu.eniso.doserunner.contracts.fixtures.ContractFixtures;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AvroJmsMessagesTest {

    private final AvroMessageCodec codec = AvroMessageCodec.forContracts();
    private final AvroJmsMessages messages = new AvroJmsMessages(codec);

    @Test
    void toMessageWritesTheSingleObjectBodyAndTheEventType() throws Exception {
        Session session = mock(Session.class);
        BytesMessage outgoing = mock(BytesMessage.class);
        when(session.createBytesMessage()).thenReturn(outgoing);
        OrderValidated event = ContractFixtures.orderValidated();

        assertThat(messages.toMessage(session, event)).isSameAs(outgoing);

        ArgumentCaptor<byte[]> body = ArgumentCaptor.forClass(byte[].class);
        verify(outgoing).writeBytes(body.capture());
        verify(outgoing).setStringProperty(MessageHeaders.EVENT_TYPE, "OrderValidated");
        assertThat(body.getValue()).isEqualTo(codec.encode(event));
    }

    @Test
    void fromMessageReadsTheWholeBody() throws Exception {
        OrderValidated event = ContractFixtures.orderValidated();
        BytesMessage incoming = bytesMessage(codec.encode(event));

        assertThat(messages.fromMessage(incoming)).isEqualTo(event);
        assertThat(messages.fromMessage(bytesMessage(codec.encode(event)), OrderValidated.class)).isEqualTo(event);
    }

    @Test
    void nonBytesMessagesAreRejected() {
        assertThatThrownBy(() -> messages.fromMessage(mock(TextMessage.class)))
                .isInstanceOf(AvroCodecException.class)
                .hasMessageContaining("Expected a BytesMessage");
    }

    private static BytesMessage bytesMessage(byte[] body) throws Exception {
        BytesMessage message = mock(BytesMessage.class);
        when(message.getBodyLength()).thenReturn((long) body.length);
        doAnswer(invocation -> {
            byte[] target = invocation.getArgument(0);
            System.arraycopy(body, 0, target, 0, body.length);
            return body.length;
        }).when(message).readBytes(any(byte[].class));
        return message;
    }
}
