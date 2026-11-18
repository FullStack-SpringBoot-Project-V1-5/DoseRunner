package tn.rnu.eniso.doserunner.contracts;

import org.apache.avro.util.ClassSecurityValidator;
import org.apache.avro.util.ClassSecurityValidator.ClassSecurityPredicate;

/**
 * Avro 1.12 refuses to instantiate classes that are not explicitly trusted (protection against
 * deserialization gadgets). Our generated contract classes must be trusted, and nothing else.
 *
 * <p>Call {@link #install()} once before decoding (idempotent). The future {@code AvroMessageCodec}
 * and {@code AvroMarshaller} call it in their static initializer, so services normally never need to.
 * Alternative without code: {@code -Dorg.apache.avro.SERIALIZABLE_PACKAGES=tn.rnu.eniso.doserunner.contracts}.
 */
public final class AvroTrust {

    public static final String CONTRACTS_PACKAGE = "tn.rnu.eniso.doserunner.contracts.";

    private static volatile boolean installed;

    public static synchronized void install() {
        if (installed) {
            return;
        }
        ClassSecurityPredicate previous = ClassSecurityValidator.getGlobal();
        ClassSecurityPredicate contracts = clazz -> clazz.getName().startsWith(CONTRACTS_PACKAGE);
        ClassSecurityValidator.setGlobal(ClassSecurityValidator.composite(previous, contracts));
        installed = true;
    }

    private AvroTrust() {
    }
}
