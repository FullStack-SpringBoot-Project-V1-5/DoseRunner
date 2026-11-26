package tn.rnu.eniso.doserunner.order.facade;

import tn.rnu.eniso.doserunner.order.dao.MedicationOrderEntity;
import tn.rnu.eniso.doserunner.order.shared.OrderStatus;

import java.time.Instant;
import java.util.UUID;

public record OrderView(
        UUID id,
        String patientRef,
        String drugCode,
        int qty,
        boolean controlled,
        String ward,
        String room,
        OrderStatus status,
        String nurseId,
        String pharmacistId,
        String rejectReason,
        Instant createdAt,
        Instant updatedAt) {

    static OrderView from(MedicationOrderEntity order) {
        return new OrderView(order.getId(), order.getPatientRef(), order.getDrugCode(), order.getQty(),
                order.isControlled(), order.getWardCode(), order.getRoom(), order.getStatus(), order.getNurseId(),
                order.getPharmacistId(), order.getRejectReason(), order.getCreatedAt(), order.getUpdatedAt());
    }
}
