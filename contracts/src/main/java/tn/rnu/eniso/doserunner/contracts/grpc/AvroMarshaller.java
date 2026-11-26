package tn.rnu.eniso.doserunner.contracts.grpc;

import io.grpc.MethodDescriptor;
import io.grpc.Status;
import org.apache.avro.specific.SpecificRecord;
import tn.rnu.eniso.doserunner.contracts.AvroCodecException;
import tn.rnu.eniso.doserunner.contracts.messaging.AvroMessageCodec;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;

public final class AvroMarshaller<T extends SpecificRecord> implements MethodDescriptor.Marshaller<T> {

    private static final AvroMessageCodec DEFAULT_CODEC = AvroMessageCodec.forContracts();

    private final Class<T> type;
    private final AvroMessageCodec codec;

    public AvroMarshaller(Class<T> type, AvroMessageCodec codec) {
        this.type = Objects.requireNonNull(type, "type");
        this.codec = Objects.requireNonNull(codec, "codec");
    }

    public static <T extends SpecificRecord> AvroMarshaller<T> of(Class<T> type) {
        return new AvroMarshaller<>(type, DEFAULT_CODEC);
    }

    public Class<T> messageType() {
        return type;
    }

    @Override
    public InputStream stream(T value) {
        try {
            return new ByteArrayInputStream(codec.encode(value));
        } catch (AvroCodecException e) {
            throw Status.INTERNAL.withDescription("Cannot encode " + type.getSimpleName()).withCause(e)
                    .asRuntimeException();
        }
    }

    @Override
    public T parse(InputStream stream) {
        try (stream) {
            return codec.decode(stream.readAllBytes(), type);
        } catch (IOException | AvroCodecException e) {
            throw Status.INTERNAL.withDescription("Cannot decode " + type.getSimpleName()).withCause(e)
                    .asRuntimeException();
        }
    }
}
