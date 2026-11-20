package tn.rnu.eniso.doserunner.contracts.messaging;

import org.apache.avro.Schema;
import org.apache.avro.SchemaNormalization;
import org.apache.avro.data.TimeConversions;
import org.apache.avro.message.BinaryMessageDecoder;
import org.apache.avro.message.BinaryMessageEncoder;
import org.apache.avro.message.SchemaStore;
import org.apache.avro.specific.SpecificData;
import org.apache.avro.specific.SpecificRecord;
import tn.rnu.eniso.doserunner.contracts.AvroCodecException;
import tn.rnu.eniso.doserunner.contracts.AvroTrust;
import tn.rnu.eniso.doserunner.contracts.ContractCatalog;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class AvroMessageCodec {

    public static final int HEADER_LENGTH = 10;

    private static final byte MARKER_0 = (byte) 0xC3;
    private static final byte MARKER_1 = (byte) 0x01;

    private final SpecificData model;
    private final SchemaStore.Cache schemaStore = new SchemaStore.Cache();
    private final Map<Long, Class<? extends SpecificRecord>> typesByFingerprint;
    private final Map<Class<?>, BinaryMessageEncoder<SpecificRecord>> encoders = new ConcurrentHashMap<>();
    private final Map<Class<?>, BinaryMessageDecoder<SpecificRecord>> decoders = new ConcurrentHashMap<>();

    public AvroMessageCodec(Collection<Class<? extends SpecificRecord>> types) {
        AvroTrust.install();
        this.model = new SpecificData(AvroMessageCodec.class.getClassLoader());
        this.model.addLogicalTypeConversion(new TimeConversions.TimestampMillisConversion());
        Map<Long, Class<? extends SpecificRecord>> index = new LinkedHashMap<>();
        for (Class<? extends SpecificRecord> type : types) {
            Schema schema = schemaOf(type);
            schemaStore.addSchema(schema);
            Class<? extends SpecificRecord> previous = index.putIfAbsent(fingerprint(schema), type);
            if (previous != null && previous != type) {
                throw new AvroCodecException("Fingerprint collision between " + previous.getName() + " and " + type.getName());
            }
        }
        this.typesByFingerprint = Map.copyOf(index);
    }

    public static AvroMessageCodec forContracts() {
        return new AvroMessageCodec(ContractCatalog.ALL);
    }

    public void registerWriterSchema(Schema writerSchema) {
        schemaStore.addSchema(Objects.requireNonNull(writerSchema, "writerSchema"));
    }

    public byte[] encode(SpecificRecord record) {
        Objects.requireNonNull(record, "record");
        BinaryMessageEncoder<SpecificRecord> encoder = encoders.computeIfAbsent(record.getClass(),
                type -> new BinaryMessageEncoder<>(model, record.getSchema()));
        try (ByteArrayOutputStream out = new ByteArrayOutputStream(64)) {
            encoder.encode(record, out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new AvroCodecException("Cannot encode " + record.getSchema().getFullName(), e);
        }
    }

    public <T extends SpecificRecord> T decode(byte[] bytes, Class<T> type) {
        Objects.requireNonNull(type, "type");
        long writerFingerprint = fingerprint(bytes);
        BinaryMessageDecoder<SpecificRecord> decoder = decoders.computeIfAbsent(type,
                key -> new BinaryMessageDecoder<>(model, schemaOf(type), schemaStore));
        try {
            return type.cast(decoder.decode(bytes));
        } catch (IOException | RuntimeException e) {
            throw new AvroCodecException("Cannot decode " + type.getSimpleName() + " from writer schema fingerprint "
                    + Long.toHexString(writerFingerprint), e);
        }
    }

    public SpecificRecord decode(byte[] bytes) {
        long writerFingerprint = fingerprint(bytes);
        Class<? extends SpecificRecord> type = typeOf(writerFingerprint)
                .orElseThrow(() -> new AvroCodecException("Unknown writer schema fingerprint "
                        + Long.toHexString(writerFingerprint)));
        return decode(bytes, type);
    }

    public Optional<Class<? extends SpecificRecord>> typeOf(long fingerprint) {
        return Optional.ofNullable(typesByFingerprint.get(fingerprint));
    }

    public static long fingerprint(byte[] bytes) {
        if (bytes == null || bytes.length < HEADER_LENGTH) {
            throw new AvroCodecException("Not an Avro single-object message: too short");
        }
        if (bytes[0] != MARKER_0 || bytes[1] != MARKER_1) {
            throw new AvroCodecException("Not an Avro single-object message: bad marker");
        }
        return ByteBuffer.wrap(bytes, 2, 8).order(ByteOrder.LITTLE_ENDIAN).getLong();
    }

    public static long fingerprint(Schema schema) {
        return SchemaNormalization.parsingFingerprint64(schema);
    }

    public static String eventType(SpecificRecord record) {
        return record.getSchema().getName();
    }

    private static Schema schemaOf(Class<? extends SpecificRecord> type) {
        return SpecificData.get().getSchema(type);
    }
}
