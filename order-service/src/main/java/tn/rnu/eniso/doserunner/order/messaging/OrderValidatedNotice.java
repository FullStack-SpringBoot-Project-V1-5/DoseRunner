package tn.rnu.eniso.doserunner.order.messaging;

import java.time.Instant;
import java.util.UUID;

public record OrderValidatedNotice(
        UUID orderId,
        String pharmacistId,
        DeliveryDestination destination,
        boolean controlled,
        Instant occurredAt) {
}
