package tn.rnu.eniso.doserunner.contracts;

import org.apache.avro.specific.SpecificRecord;
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

import java.util.List;
import java.util.stream.Stream;

public final class ContractCatalog {

    public static final List<Class<? extends SpecificRecord>> EVENTS = List.of(
            OrderPlaced.class,
            OrderValidated.class,
            RobotAssigned.class,
            PickupConfirmed.class,
            DeliveryConfirmed.class,
            DispatchTimedOut.class,
            MissionAborted.class);

    public static final List<Class<? extends SpecificRecord>> ROBOT = List.of(
            MissionCommand.class,
            RobotTelemetry.class);

    public static final List<Class<? extends SpecificRecord>> GRPC = List.of(
            EtaRequest.class,
            EtaReply.class,
            ReserveRequest.class,
            ReserveReply.class);

    public static final List<Class<? extends SpecificRecord>> MESSAGES = concat(EVENTS, ROBOT);

    public static final List<Class<? extends SpecificRecord>> ALL = concat(MESSAGES, GRPC);

    private static List<Class<? extends SpecificRecord>> concat(List<Class<? extends SpecificRecord>> first,
                                                               List<Class<? extends SpecificRecord>> second) {
        return Stream.concat(first.stream(), second.stream()).toList();
    }

    private ContractCatalog() {
    }
}
