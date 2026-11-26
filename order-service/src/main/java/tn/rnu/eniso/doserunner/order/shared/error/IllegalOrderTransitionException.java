package tn.rnu.eniso.doserunner.order.shared.error;

import tn.rnu.eniso.doserunner.order.shared.OrderStatus;

import java.util.UUID;

public class IllegalOrderTransitionException extends OrderServiceException {

    public IllegalOrderTransitionException(UUID orderId, OrderStatus from, OrderStatus to) {
        super(ErrorKind.CONFLICT, "Order " + orderId + " cannot move from " + from + " to " + to);
    }
}
