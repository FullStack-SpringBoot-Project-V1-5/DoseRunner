package tn.rnu.eniso.doserunner.order.messaging;

import tn.rnu.eniso.doserunner.contracts.common.GridPoint;
import tn.rnu.eniso.doserunner.contracts.common.Location;
import tn.rnu.eniso.doserunner.contracts.events.OrderPlaced;
import tn.rnu.eniso.doserunner.contracts.events.OrderValidated;

import java.time.temporal.ChronoUnit;
import java.util.UUID;

final class OrderEventMapper {

    static OrderPlaced toAvro(OrderPlacedNotice notice) {
        return OrderPlaced.newBuilder()
                .setEventId(UUID.randomUUID().toString())
                .setOccurredAt(notice.occurredAt().truncatedTo(ChronoUnit.MILLIS))
                .setOrderId(notice.orderId().toString())
                .setPatientRef(notice.patientRef())
                .setDrugCode(notice.drugCode())
                .setQty(notice.qty())
                .setWardLocation(toLocation(notice.destination()))
                .setControlled(notice.controlled())
                .setNurseId(notice.nurseId())
                .build();
    }

    static OrderValidated toAvro(OrderValidatedNotice notice) {
        return OrderValidated.newBuilder()
                .setEventId(UUID.randomUUID().toString())
                .setOccurredAt(notice.occurredAt().truncatedTo(ChronoUnit.MILLIS))
                .setOrderId(notice.orderId().toString())
                .setPharmacistId(notice.pharmacistId())
                .setDestination(toLocation(notice.destination()))
                .setControlled(notice.controlled())
                .build();
    }

    private static Location toLocation(DeliveryDestination destination) {
        return Location.newBuilder()
                .setWard(destination.ward())
                .setRoom(destination.room())
                .setPoint(new GridPoint(destination.gridX(), destination.gridY()))
                .build();
    }

    private OrderEventMapper() {
    }
}
