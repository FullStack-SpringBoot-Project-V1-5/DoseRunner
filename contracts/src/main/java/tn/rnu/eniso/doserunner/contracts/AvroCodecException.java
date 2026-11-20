package tn.rnu.eniso.doserunner.contracts;

public class AvroCodecException extends RuntimeException {

    public AvroCodecException(String message) {
        super(message);
    }

    public AvroCodecException(String message, Throwable cause) {
        super(message, cause);
    }
}
