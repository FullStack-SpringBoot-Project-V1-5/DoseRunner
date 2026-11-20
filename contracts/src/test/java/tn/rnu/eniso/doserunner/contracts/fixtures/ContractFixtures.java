package tn.rnu.eniso.doserunner.contracts.fixtures;

import org.apache.avro.specific.SpecificRecord;
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

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

public final class ContractFixtures {

    public static final Instant NOW = Instant.parse("2026-11-20T10:15:30.123Z").truncatedTo(ChronoUnit.MILLIS);

    public static final Location WARD = Location.newBuilder()
            .setWard("CARDIO-2").setRoom("214").setPoint(new GridPoint(12, 3)).build();

    public static final Location PHARMACY = Location.newBuilder()
            .setWard("PHARMACY").setPoint(new GridPoint(0, 0)).build();

    public static final List<GridPoint> PATH = List.of(new GridPoint(0, 0), new GridPoint(1, 0), new GridPoint(12, 3));

    public static OrderPlaced orderPlaced() {
        return OrderPlaced.newBuilder().setEventId("e1").setOccurredAt(NOW).setOrderId("o1")
                .setPatientRef("P-0042").setDrugCode("MORPH-10").setQty(2).setWardLocation(WARD)
                .setControlled(true).setNurseId("u-nurse").build();
    }

    public static OrderValidated orderValidated() {
        return OrderValidated.newBuilder().setEventId("e2").setOccurredAt(NOW).setOrderId("o1")
                .setPharmacistId("u-pharma").setDestination(WARD).setControlled(true).build();
    }

    public static RobotTelemetry robotTelemetry() {
        return RobotTelemetry.newBuilder().setRobotId("R-01").setX(3).setY(4).setBattery(87).build();
    }

    public static EtaRequest etaRequest() {
        return EtaRequest.newBuilder().setOrder(new OrderRef("o1")).setDestination(PHARMACY).setControlled(false).build();
    }

    public static EtaReply etaReply() {
        return EtaReply.newBuilder().setOrder(new OrderRef("o1")).setAvailable(true).setEtaSeconds(60)
                .setRobot(new RobotRef("R-01")).build();
    }

    public static ReserveRequest reserveRequest() {
        return ReserveRequest.newBuilder().setOrder(new OrderRef("o1")).setDestination(WARD).setControlled(true).build();
    }

    public static ReserveReply reserveReply() {
        return ReserveReply.newBuilder().setOrder(new OrderRef("o1")).setReserved(false).setReason("no idle robot").build();
    }

    public static List<SpecificRecord> messages() {
        return List.of(
                orderPlaced(),
                orderValidated(),
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
                robotTelemetry());
    }

    public static List<SpecificRecord> grpcMessages() {
        return List.of(etaRequest(), etaReply(), reserveRequest(), reserveReply());
    }

    private ContractFixtures() {
    }
}
