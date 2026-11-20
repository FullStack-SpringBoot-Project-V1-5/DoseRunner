package tn.rnu.eniso.doserunner.order.bo;

import org.springframework.stereotype.Component;
import tn.rnu.eniso.doserunner.order.dao.DrugEntity;
import tn.rnu.eniso.doserunner.order.dao.MedicationOrderEntity;
import tn.rnu.eniso.doserunner.order.dao.WardEntity;
import tn.rnu.eniso.doserunner.order.shared.OrderStatus;
import tn.rnu.eniso.doserunner.order.shared.error.IllegalOrderTransitionException;
import tn.rnu.eniso.doserunner.order.shared.error.InvalidOrderException;

import java.time.Instant;
import java.util.UUID;

@Component
public class OrderBO {

    public static final int MAX_QTY = 100;
    public static final int MAX_CONTROLLED_QTY = 10;

    public MedicationOrderEntity create(UUID id, String patientRef, DrugEntity drug, int qty, WardEntity ward,
                                        String room, String nurseId, Instant now) {
        if (patientRef == null || patientRef.isBlank()) {
            throw new InvalidOrderException("patientRef is required");
        }
        if (nurseId == null || nurseId.isBlank()) {
            throw new InvalidOrderException("nurseId is required");
        }
        if (qty <= 0 || qty > MAX_QTY) {
            throw new InvalidOrderException("qty must be between 1 and " + MAX_QTY + ", got " + qty);
        }
        if (drug.isControlled() && qty > MAX_CONTROLLED_QTY) {
            throw new InvalidOrderException("Controlled substance " + drug.getCode() + " is limited to "
                    + MAX_CONTROLLED_QTY + " units per order");
        }
        if (!ward.isDeliverable()) {
            throw new InvalidOrderException("Ward " + ward.getCode() + " is not a delivery destination");
        }
        String normalisedRoom = room == null || room.isBlank() ? null : room.strip();
        return new MedicationOrderEntity(id, patientRef.strip(), drug.getCode(), qty, drug.isControlled(),
                ward.getCode(), normalisedRoom, nurseId, now);
    }

    public void validate(MedicationOrderEntity order, String pharmacistId, Instant now) {
        requirePharmacist(pharmacistId);
        moveTo(order, OrderStatus.VALIDATED, now);
        order.setPharmacistId(pharmacistId);
    }

    public void reject(MedicationOrderEntity order, String pharmacistId, String reason, Instant now) {
        requirePharmacist(pharmacistId);
        if (reason == null || reason.isBlank()) {
            throw new InvalidOrderException("A reason is required to reject an order");
        }
        moveTo(order, OrderStatus.REJECTED, now);
        order.setPharmacistId(pharmacistId);
        order.setRejectReason(reason.strip());
    }

    private static void moveTo(MedicationOrderEntity order, OrderStatus next, Instant now) {
        if (!order.getStatus().canMoveTo(next)) {
            throw new IllegalOrderTransitionException(order.getId(), order.getStatus(), next);
        }
        order.setStatus(next);
        order.setUpdatedAt(now);
    }

    private static void requirePharmacist(String pharmacistId) {
        if (pharmacistId == null || pharmacistId.isBlank()) {
            throw new InvalidOrderException("pharmacistId is required");
        }
    }
}
