package tn.rnu.eniso.doserunner.contracts.messaging;

import org.apache.avro.Schema;
import org.apache.avro.SchemaBuilder;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericRecord;
import org.apache.avro.message.BinaryMessageEncoder;
import org.apache.avro.specific.SpecificRecord;
import org.junit.jupiter.api.Test;
import tn.rnu.eniso.doserunner.contracts.AvroCodecException;
import tn.rnu.eniso.doserunner.contracts.ContractCatalog;
import tn.rnu.eniso.doserunner.contracts.events.OrderPlaced;
import tn.rnu.eniso.doserunner.contracts.events.OrderValidated;
import tn.rnu.eniso.doserunner.contracts.fixtures.ContractFixtures;
import tn.rnu.eniso.doserunner.contracts.robot.RobotTelemetry;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AvroMessageCodecTest {

    private final AvroMessageCodec codec = AvroMessageCodec.forContracts();

    @Test
    void everyMessageRoundTripsWithItsDeclaredType() {
        for (SpecificRecord original : ContractFixtures.messages()) {
            byte[] bytes = codec.encode(original);
            SpecificRecord decoded = codec.decode(bytes, original.getClass());
            assertThat(decoded).as(original.getSchema().getName()).isEqualTo(original);
        }
    }

    @Test
    void decodeWithoutTypeResolvesTheClassFromTheFingerprint() {
        for (SpecificRecord original : ContractFixtures.messages()) {
            SpecificRecord decoded = codec.decode(codec.encode(original));
            assertThat(decoded).isInstanceOf(original.getClass()).isEqualTo(original);
        }
    }

    @Test
    void encodedBytesAreIdenticalToTheGeneratedClassEncoding() throws Exception {
        OrderValidated event = ContractFixtures.orderValidated();
        ByteBuffer generated = event.toByteBuffer();
        byte[] expected = new byte[generated.remaining()];
        generated.get(expected);

        assertThat(codec.encode(event)).isEqualTo(expected);
    }

    @Test
    void headerCarriesMarkerAndWriterSchemaFingerprint() {
        byte[] bytes = codec.encode(ContractFixtures.orderPlaced());

        assertThat(bytes[0]).isEqualTo((byte) 0xC3);
        assertThat(bytes[1]).isEqualTo((byte) 0x01);
        assertThat(AvroMessageCodec.fingerprint(bytes))
                .isEqualTo(AvroMessageCodec.fingerprint(OrderPlaced.getClassSchema()));
        assertThat(codec.typeOf(AvroMessageCodec.fingerprint(bytes))).contains(OrderPlaced.class);
    }

    @Test
    void decodingIntoTheWrongTypeFailsLoudly() {
        byte[] telemetry = codec.encode(ContractFixtures.robotTelemetry());

        assertThatThrownBy(() -> codec.decode(telemetry, OrderValidated.class))
                .isInstanceOf(AvroCodecException.class)
                .hasMessageContaining("OrderValidated");
    }

    @Test
    void unknownFingerprintIsRejected() {
        AvroMessageCodec eventsOnly = new AvroMessageCodec(ContractCatalog.EVENTS);
        byte[] telemetry = codec.encode(ContractFixtures.robotTelemetry());

        assertThatThrownBy(() -> eventsOnly.decode(telemetry))
                .isInstanceOf(AvroCodecException.class)
                .hasMessageContaining("Unknown writer schema fingerprint");
    }

    @Test
    void bytesWithoutTheSingleObjectMarkerAreRejected() {
        byte[] bytes = codec.encode(ContractFixtures.orderPlaced());
        bytes[0] = 0x00;

        assertThatThrownBy(() -> codec.decode(bytes)).isInstanceOf(AvroCodecException.class)
                .hasMessageContaining("bad marker");
        assertThatThrownBy(() -> codec.decode(new byte[3])).isInstanceOf(AvroCodecException.class)
                .hasMessageContaining("too short");
        assertThatThrownBy(() -> codec.decode(null)).isInstanceOf(AvroCodecException.class);
    }

    @Test
    void truncatedPayloadIsRejected() {
        byte[] bytes = codec.encode(ContractFixtures.orderPlaced());
        byte[] truncated = Arrays.copyOf(bytes, bytes.length - 5);

        assertThatThrownBy(() -> codec.decode(truncated, OrderPlaced.class)).isInstanceOf(AvroCodecException.class);
    }

    @Test
    void readerOnCurrentSchemaReadsBytesFromANewerRegisteredWriterSchema() throws Exception {
        Schema newerTelemetry = SchemaBuilder.record("RobotTelemetry")
                .namespace("tn.rnu.eniso.doserunner.contracts.robot")
                .fields()
                .requiredString("robotId")
                .requiredInt("x")
                .requiredInt("y")
                .requiredInt("battery")
                .name("temperatureC").type().unionOf().nullType().and().doubleType().endUnion().nullDefault()
                .endRecord();
        GenericRecord written = new GenericData.Record(newerTelemetry);
        written.put("robotId", "R-02");
        written.put("x", 7);
        written.put("y", 1);
        written.put("battery", 64);
        written.put("temperatureC", 36.5);
        byte[] bytes = new BinaryMessageEncoder<GenericRecord>(GenericData.get(), newerTelemetry)
                .encode(written).array();

        assertThatThrownBy(() -> codec.decode(bytes, RobotTelemetry.class)).isInstanceOf(AvroCodecException.class);

        codec.registerWriterSchema(newerTelemetry);
        RobotTelemetry read = codec.decode(bytes, RobotTelemetry.class);

        assertThat(read.getRobotId()).isEqualTo("R-02");
        assertThat(read.getBattery()).isEqualTo(64);
    }

    @Test
    void eventTypeIsTheRecordSimpleName() {
        assertThat(AvroMessageCodec.eventType(ContractFixtures.orderValidated())).isEqualTo("OrderValidated");
    }

    @Test
    void catalogListsEveryGeneratedContractOnce() {
        assertThat(ContractCatalog.ALL).doesNotHaveDuplicates().hasSize(13);
        assertThat(ContractCatalog.MESSAGES).containsAll(List.of(OrderPlaced.class, RobotTelemetry.class));
    }
}
