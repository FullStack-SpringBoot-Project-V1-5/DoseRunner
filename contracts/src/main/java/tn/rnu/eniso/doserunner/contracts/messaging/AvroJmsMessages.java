package tn.rnu.eniso.doserunner.contracts.messaging;

import jakarta.jms.BytesMessage;
import jakarta.jms.JMSException;
import jakarta.jms.Message;
import jakarta.jms.Session;
import org.apache.avro.specific.SpecificRecord;
import tn.rnu.eniso.doserunner.contracts.AvroCodecException;

import java.util.Objects;

public final class AvroJmsMessages {

    private final AvroMessageCodec codec;

    public AvroJmsMessages(AvroMessageCodec codec) {
        this.codec = Objects.requireNonNull(codec, "codec");
    }

    public BytesMessage toMessage(Session session, SpecificRecord record) throws JMSException {
        BytesMessage message = session.createBytesMessage();
        message.writeBytes(codec.encode(record));
        message.setStringProperty(MessageHeaders.EVENT_TYPE, AvroMessageCodec.eventType(record));
        return message;
    }

    public SpecificRecord fromMessage(Message message) throws JMSException {
        return codec.decode(bodyOf(message));
    }

    public <T extends SpecificRecord> T fromMessage(Message message, Class<T> type) throws JMSException {
        return codec.decode(bodyOf(message), type);
    }

    private static byte[] bodyOf(Message message) throws JMSException {
        if (!(message instanceof BytesMessage bytesMessage)) {
            throw new AvroCodecException("Expected a BytesMessage but got "
                    + (message == null ? "null" : message.getClass().getName()));
        }
        bytesMessage.reset();
        long length = bytesMessage.getBodyLength();
        if (length > Integer.MAX_VALUE) {
            throw new AvroCodecException("Message body too large: " + length + " bytes");
        }
        byte[] body = new byte[(int) length];
        bytesMessage.readBytes(body);
        return body;
    }
}
