package tn.rnu.eniso.doserunner.nativelib;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;

import static java.lang.foreign.ValueLayout.JAVA_INT;

/**
 * Day-3 gate: the smallest possible FFM call. Loads libsodium, calls {@code sodium_init()} once, prints its return
 * value and exits with it. Expected output in the {@code ffm-smoke} Docker image: {@code sodium_init() = 0}.
 *
 * <pre>
 * docker build -f deploy/compose/Dockerfile.native --target ffm-smoke -t doserunner/ffm-smoke .
 * docker run --rm doserunner/ffm-smoke
 * </pre>
 *
 * Deliberately does not go through {@link Sodium}, which already calls {@code sodium_init()} (a second call returns 1).
 */
public final class SodiumInitCheck {

    private SodiumInitCheck() {
    }

    public static void main(String[] args) throws Throwable {
        String lib = args.length > 0 ? args[0] : System.getProperty(Sodium.LIBRARY_PROPERTY, Sodium.DEFAULT_LIBRARY);
        try (Arena arena = Arena.ofConfined()) {
            SymbolLookup sodium = SymbolLookup.libraryLookup(lib, arena);
            MethodHandle sodiumInit = Linker.nativeLinker().downcallHandle(
                    sodium.find("sodium_init").orElseThrow(), FunctionDescriptor.of(JAVA_INT));
            int rc = (int) sodiumInit.invokeExact();
            System.out.println("sodium_init() = " + rc);
            System.exit(rc);
        }
    }
}
