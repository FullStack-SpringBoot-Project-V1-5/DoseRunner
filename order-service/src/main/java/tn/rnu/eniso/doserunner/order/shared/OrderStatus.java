package tn.rnu.eniso.doserunner.order.shared;

import java.util.EnumSet;
import java.util.Set;

public enum OrderStatus {

    PLACED,
    VALIDATED,
    ASSIGNED,
    PICKED_UP,
    COMPLETED,
    REJECTED,
    MANUAL_DELIVERY;

    public Set<OrderStatus> allowedNext() {
        return switch (this) {
            case PLACED -> EnumSet.of(VALIDATED, REJECTED);
            case VALIDATED -> EnumSet.of(ASSIGNED, MANUAL_DELIVERY);
            case ASSIGNED -> EnumSet.of(PICKED_UP, MANUAL_DELIVERY);
            case PICKED_UP -> EnumSet.of(COMPLETED, MANUAL_DELIVERY);
            case MANUAL_DELIVERY -> EnumSet.of(COMPLETED);
            case COMPLETED, REJECTED -> EnumSet.noneOf(OrderStatus.class);
        };
    }

    public boolean canMoveTo(OrderStatus next) {
        return allowedNext().contains(next);
    }

    public boolean isTerminal() {
        return allowedNext().isEmpty();
    }
}
