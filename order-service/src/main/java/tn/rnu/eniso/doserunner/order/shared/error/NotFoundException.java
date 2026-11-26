package tn.rnu.eniso.doserunner.order.shared.error;

public class NotFoundException extends OrderServiceException {

    public NotFoundException(String resource, Object id) {
        super(ErrorKind.NOT_FOUND, resource + " '" + id + "' not found");
    }
}
