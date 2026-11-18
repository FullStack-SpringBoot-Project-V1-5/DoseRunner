package tn.rnu.eniso.doserunner.contracts;

import org.apache.avro.Schema;
import org.apache.avro.SchemaNormalization;
import org.apache.avro.message.MissingSchemaException;
import org.apache.avro.specific.SpecificRecordBase;
import org.junit.jupiter.api.Test;
import tn.rnu.eniso.doserunner.contracts.common.GridPoint;
import tn.rnu.eniso.doserunner.contracts.common.Location;
import tn.rnu.eniso.doserunner.contracts.common.OrderRef;
import tn.rnu.eniso.doserunner.contracts.common.RobotRef;
import tn.rnu.eniso.doserunner.contracts.events.AbortReason;
import tn.rnu.eniso.doserunner.contracts.events.DeliveryConfirmed;
import tn.rnu.eniso.doserunner.contracts.events.DispatchTimedOut;
import tn.rnu.eniso.doserunner.contracts.events.MissionAborted;
import tn.rnu.eniso.doserunner.contracts.events.OrderPlaced;
import tn.rnu.eniso.doserunner.contracts.events.OrderValidated;
import tn.rnu.eniso.doserunner.contracts.events.PickupConfirmed;
import tn.rnu.eniso.doserunner.contracts.events.RobotAssigned;
import tn.rnu.eniso.doserunner.contracts.grpc.EtaReply;
import tn.rnu.eniso.doserunner.contracts.grpc.EtaRequest;
import tn.rnu.eniso.doserunner.contracts.grpc.ReserveReply;
import tn.rnu.eniso.doserunner.contracts.grpc.ReserveRequest;
import tn.rnu.eniso.doserunner.contracts.robot.MissionCommand;
import tn.rnu.eniso.doserunner.contracts.robot.RobotTelemetry;

import java.nio.ByteBuffer;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Every contract must survive Avro single-object encoding (the wire format on Artemis), and the encoded
 * bytes must start with the marker + the writer schema's CRC-64-AVRO fingerprint.
 */
class ContractsRoundTripTest {

    @org.junit.jupiter.api.BeforeAll
    static void trustContracts() {
        AvroTrust.install();
    }

    // timestamp-millis drops sub-millisecond precision: truncate so equals() holds after decoding
    private static final Instant NOW = Instant.now().truncatedTo(ChronoUnit.MILLIS);
    private static final Location WARD = Location.newBuilder()
            .setWard("CARDIO-2").setRoom("214").setPoint(new GridPoint(12, 3)).build();
    private static final Location PHARMACY = Location.newBuilder()
            .setWard("PHARMACY").setPoint(new GridPoint(0, 0)).build();
    private static final List<GridPoint> PATH = List.of(new GridPoint(0, 0), new GridPoint(1, 0), new GridPoint(12, 3));

    static List<SpecificRecordBase> samples() {
        return List.of(
                OrderPlaced.newBuilder().setEventId("e1").setOccurredAt(NOW).setOrderId("o1")
                        .setPatientRef("P-0042").setDrugCode("MORPH-10").setQty(2).setWardLocation(WARD)
                        .setControlled(true).setNurseId("u-nurse").build(),
                OrderValidated.newBuilder().setEventId("e2").setOccurredAt(NOW).setOrderId("o1")
                        .setPharmacistId("u-pharma").setDestination(WARD).setControlled(true).build(),
                RobotAssigned.newBuilder().setEventId("e3").setOccurredAt(NOW).setOrderId("o1")
                        .setRobotId("R-01").setMissionId("m1").setEtaSeconds(90).setPath(PATH).build(),
                PickupConfirmed.newBuilder().setEventId("e4").setOccurredAt(NOW).setOrderId("o1")
                        .setRobotId("R-01").setPharmacistId("u-pharma").build(),
                DeliveryConfirmed.newBuilder().setEventId("e5").setOccurredAt(NOW).setOrderId("o1")
                        .setRobotId("R-01").setNurseId("u-nurse").build(),
                DispatchTimedOut.newBuilder().setEventId("e6").setOccurredAt(NOW).setOrderId("o1").build(),
                MissionAborted.newBuilder().setEventId("e7").setOccurredAt(NOW).setOrderId("o1")
                        .setRobotId("R-01").setMissionId("m1").setReason(AbortReason.LOW_BATTERY).build(),
                MissionCommand.newBuilder().setMissionId("m1").setRobotId("R-01").setOrderId("o1").setSeq(1)
                        .setWaypoints(PATH).setCompartmentCode("C2").setIssuedAt(NOW).build(),
                RobotTelemetry.newBuilder().setRobotId("R-01").setX(3).setY(4).setBattery(87).build(),
                EtaRequest.newBuilder().setOrder(new OrderRef("o1")).setDestination(PHARMACY).setControlled(false).build(),
                EtaReply.newBuilder().setOrder(new OrderRef("o1")).setAvailable(true).setEtaSeconds(60)
                        .setRobot(new RobotRef("R-01")).build(),
                ReserveRequest.newBuilder().setOrder(new OrderRef("o1")).setDestination(WARD).setControlled(true).build(),
                ReserveReply.newBuilder().setOrder(new OrderRef("o1")).setReserved(false).setReason("no idle robot").build());
    }

    @Test
    void everyContractRoundTripsWithSingleObjectEncoding() throws Exception {
        for (SpecificRecordBase original : samples()) {
            ByteBuffer encoded = (ByteBuffer) original.getClass().getMethod("toByteBuffer").invoke(original);
            Object decoded = original.getClass().getMethod("fromByteBuffer", ByteBuffer.class)
                    .invoke(null, encoded.duplicate());
            assertThat(decoded).as(original.getSchema().getName()).isEqualTo(original);
        }
    }

    @Test
    void encodedBytesCarryMarkerAndWriterFingerprint() throws Exception {
        OrderValidated event = (OrderValidated) samples().get(1);
        ByteBuffer bytes = event.toByteBuffer();

        assertThat(bytes.get(0)).isEqualTo((byte) 0xC3);
        assertThat(bytes.get(1)).isEqualTo((byte) 0x01);
        long fingerprint = bytes.order(java.nio.ByteOrder.LITTLE_ENDIAN).getLong(2);
        assertThat(fingerprint).isEqualTo(SchemaNormalization.parsingFingerprint64(OrderValidated.getClassSchema()));
    }

    @Test
    void decoderRejectsBytesWrittenWithAnotherSchema() throws Exception {
        ByteBuffer telemetry = ((RobotTelemetry) samples().get(8)).toByteBuffer();
        // An OrderValidated decoder has no idea what that fingerprint is: it must fail loudly, never misread.
        assertThatThrownBy(() -> OrderValidated.fromByteBuffer(telemetry)).isInstanceOf(MissingSchemaException.class);
    }

    @Test
    void defaultsAreAppliedForOptionalFields() {
        ReserveRequest req = ReserveRequest.newBuilder().setOrder(new OrderRef("o1")).setDestination(WARD)
                .setControlled(false).build();
        assertThat(req.getHoldSeconds()).isEqualTo(120);
        assertThat(PHARMACY.getRoom()).isNull();
        MissionAborted aborted = (MissionAborted) samples().get(6);
        assertThat(aborted.getDetail()).isNull();
    }

    @Test
    void allContractsLiveUnderTheContractsNamespace() {
        for (SpecificRecordBase r : samples()) {
            Schema s = r.getSchema();
            assertThat(s.getNamespace()).startsWith("tn.rnu.eniso.doserunner.contracts.");
        }
    }
}
