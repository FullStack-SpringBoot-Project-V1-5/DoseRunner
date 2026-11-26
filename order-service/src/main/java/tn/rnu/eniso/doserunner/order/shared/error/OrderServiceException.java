package tn.rnu.eniso.doserunner.order.shared.error;

public abstract class OrderServiceException extends RuntimeException {

    private final ErrorKind kind;

    protected OrderServiceException(ErrorKind kind, String message) {
        super(message);
        this.kind = kind;
    }

    public ErrorKind kind() {
        return kind;
    }
}
