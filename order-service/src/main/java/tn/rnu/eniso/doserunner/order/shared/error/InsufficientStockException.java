package tn.rnu.eniso.doserunner.order.shared.error;

public class InsufficientStockException extends OrderServiceException {

    private final String drugCode;
    private final int requested;
    private final int available;

    public InsufficientStockException(String drugCode, int requested, int available) {
        super(ErrorKind.CONFLICT, "Not enough stock for " + drugCode + ": requested " + requested
                + ", available " + available);
        this.drugCode = drugCode;
        this.requested = requested;
        this.available = available;
    }

    public String drugCode() {
        return drugCode;
    }

    public int requested() {
        return requested;
    }

    public int available() {
        return available;
    }
}
