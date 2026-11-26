package tn.rnu.eniso.doserunner.order.messaging;

import java.time.Instant;
import java.util.UUID;

public record OrderPlacedNotice(
        UUID orderId,
        String patientRef,
        String drugCode,
        int qty,
        DeliveryDestination destination,
        boolean controlled,
        String nurseId,
        Instant occurredAt) {
}
