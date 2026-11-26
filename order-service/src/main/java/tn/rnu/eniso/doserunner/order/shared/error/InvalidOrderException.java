package tn.rnu.eniso.doserunner.order.shared.error;

public class InvalidOrderException extends OrderServiceException {

    public InvalidOrderException(String message) {
        super(ErrorKind.INVALID, message);
    }
}
