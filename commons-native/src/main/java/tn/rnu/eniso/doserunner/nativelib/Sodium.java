package tn.rnu.eniso.doserunner.nativelib;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;
import java.util.Arrays;
import java.util.Objects;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_BYTE;
import static java.lang.foreign.ValueLayout.JAVA_INT;
import static java.lang.foreign.ValueLayout.JAVA_LONG;

/**
 * libsodium through the FFM API (no JNI): Ed25519 detached signatures and BLAKE2b ({@code crypto_generichash}).
 *
 * <p>Two lifetimes, two arenas:
 * <ul>
 *   <li>the library itself is loaded once into {@link Arena#global()}: it stays mapped for the whole JVM;</li>
 *   <li>every call copies its byte arrays into an {@link Arena#ofConfined()} opened in try-with-resources, so the
 *       native buffers are freed (and secret-key copies zeroed) as soon as the call returns.</li>
 * </ul>
 *
 * <p>The API only takes and returns {@code byte[]}: no {@link MemorySegment} escapes this class.
 * Instances are thread-safe (method handles are immutable, each call has its own confined arena).
 *
 * <p>The library name defaults to {@code libsodium.so.23} (Debian/Ubuntu package {@code libsodium23}) and can be
 * overridden with {@code -Ddoserunner.sodium.lib=...} (e.g. {@code libsodium.dll} or an absolute path).
 */
public final class Sodium {

    public static final String LIBRARY_PROPERTY = "doserunner.sodium.lib";
    public static final String DEFAULT_LIBRARY = "libsodium.so.23";

    /** {@code crypto_sign_BYTES}: Ed25519 signature length. */
    public static final int SIGNATURE_BYTES = 64;
    /** {@code crypto_sign_PUBLICKEYBYTES}. */
    public static final int PUBLIC_KEY_BYTES = 32;
    /** {@code crypto_sign_SECRETKEYBYTES}: libsodium's secret key is seed (32) || public key (32). */
    public static final int SECRET_KEY_BYTES = 64;
    /** {@code crypto_generichash_BYTES}: BLAKE2b-256. */
    public static final int HASH_BYTES = 32;

    private static volatile Sodium instance;

    private final MethodHandle sodiumInit;
    private final MethodHandle signKeypair;
    private final MethodHandle signDetached;
    private final MethodHandle verifyDetached;
    private final MethodHandle genericHash;

    private Sodium(String libraryName) {
        Linker linker = Linker.nativeLinker();
        SymbolLookup lib = SymbolLookup.libraryLookup(libraryName, Arena.global());

        // int sodium_init(void);
        sodiumInit = bind(linker, lib, "sodium_init", FunctionDescriptor.of(JAVA_INT));
        // int crypto_sign_keypair(unsigned char *pk, unsigned char *sk);
        signKeypair = bind(linker, lib, "crypto_sign_keypair", FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS));
        // int crypto_sign_detached(unsigned char *sig, unsigned long long *siglen_p,
        //                          const unsigned char *m, unsigned long long mlen, const unsigned char *sk);
        signDetached = bind(linker, lib, "crypto_sign_detached",
                FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, ADDRESS, JAVA_LONG, ADDRESS));
        // int crypto_sign_verify_detached(const unsigned char *sig, const unsigned char *m,
        //                                 unsigned long long mlen, const unsigned char *pk);
        verifyDetached = bind(linker, lib, "crypto_sign_verify_detached",
                FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, JAVA_LONG, ADDRESS));
        // int crypto_generichash(unsigned char *out, size_t outlen, const unsigned char *in,
        //                        unsigned long long inlen, const unsigned char *key, size_t keylen);
        genericHash = bind(linker, lib, "crypto_generichash",
                FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_LONG, ADDRESS, JAVA_LONG, ADDRESS, JAVA_LONG));

        int rc = init();
        if (rc < 0) {
            throw new IllegalStateException("sodium_init() failed: " + rc);
        }
    }

    /**
     * Loads libsodium (once per JVM) and runs {@code sodium_init()}.
     *
     * @throws IllegalArgumentException if the library cannot be found (message names the library and the property)
     */
    public static Sodium get() {
        Sodium s = instance;
        if (s == null) {
            synchronized (Sodium.class) {
                s = instance;
                if (s == null) {
                    String name = System.getProperty(LIBRARY_PROPERTY, DEFAULT_LIBRARY);
                    try {
                        s = new Sodium(name);
                    } catch (IllegalArgumentException e) {
                        throw new IllegalArgumentException("Cannot load " + name + " (install libsodium23, or set -D"
                                + LIBRARY_PROPERTY + "=<path>)", e);
                    }
                    instance = s;
                }
            }
        }
        return s;
    }

    /** {@code sodium_init()}: 0 = initialised now, 1 = already initialised, -1 = failure. */
    public int init() {
        try {
            return (int) sodiumInit.invokeExact();
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    /** {@code crypto_sign_keypair}: a fresh random Ed25519 key pair. */
    public KeyPair generateKeyPair() {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment pk = arena.allocate(PUBLIC_KEY_BYTES);
            MemorySegment sk = arena.allocate(SECRET_KEY_BYTES);
            check("crypto_sign_keypair", (int) signKeypair.invokeExact(pk, sk));
            KeyPair kp = new KeyPair(pk.toArray(JAVA_BYTE), sk.toArray(JAVA_BYTE));
            sk.fill((byte) 0);
            return kp;
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    /** {@code crypto_sign_detached}: 64-byte Ed25519 signature of {@code message}. */
    public byte[] sign(byte[] message, byte[] secretKey) {
        Objects.requireNonNull(message, "message");
        requireLength(secretKey, SECRET_KEY_BYTES, "secretKey");
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment sig = arena.allocate(SIGNATURE_BYTES);
            MemorySegment m = copyIn(arena, message);
            MemorySegment sk = copyIn(arena, secretKey);
            try {
                check("crypto_sign_detached",
                        (int) signDetached.invokeExact(sig, MemorySegment.NULL, m, (long) message.length, sk));
            } finally {
                sk.fill((byte) 0);
            }
            return sig.toArray(JAVA_BYTE);
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    /** {@code crypto_sign_verify_detached}: true only if {@code signature} is valid for {@code message} and {@code publicKey}. */
    public boolean verify(byte[] signature, byte[] message, byte[] publicKey) {
        Objects.requireNonNull(message, "message");
        if (signature == null || signature.length != SIGNATURE_BYTES
                || publicKey == null || publicKey.length != PUBLIC_KEY_BYTES) {
            return false;
        }
        try (Arena arena = Arena.ofConfined()) {
            int rc = (int) verifyDetached.invokeExact(
                    copyIn(arena, signature), copyIn(arena, message), (long) message.length, copyIn(arena, publicKey));
            return rc == 0;
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    /** {@code crypto_generichash} without key: BLAKE2b-256 of {@code input}. */
    public byte[] hash(byte[] input) {
        Objects.requireNonNull(input, "input");
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment out = arena.allocate(HASH_BYTES);
            check("crypto_generichash", (int) genericHash.invokeExact(
                    out, (long) HASH_BYTES, copyIn(arena, input), (long) input.length, MemorySegment.NULL, 0L));
            return out.toArray(JAVA_BYTE);
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    /** Ed25519 key pair as libsodium lays it out. {@link #toString()} never prints the secret key. */
    public record KeyPair(byte[] publicKey, byte[] secretKey) {
        public KeyPair {
            requireLength(publicKey, PUBLIC_KEY_BYTES, "publicKey");
            requireLength(secretKey, SECRET_KEY_BYTES, "secretKey");
            publicKey = publicKey.clone();
            secretKey = secretKey.clone();
            // libsodium's sk ends with the public key: a mismatch means a corrupted or foreign key file.
            if (!Arrays.equals(secretKey, PUBLIC_KEY_BYTES, SECRET_KEY_BYTES, publicKey, 0, PUBLIC_KEY_BYTES)) {
                throw new IllegalArgumentException("secretKey does not match publicKey");
            }
        }

        /** Rebuilds the pair from libsodium's 64-byte secret key (its last 32 bytes are the public key). */
        public static KeyPair fromSecretKey(byte[] secretKey) {
            requireLength(secretKey, SECRET_KEY_BYTES, "secretKey");
            return new KeyPair(Arrays.copyOfRange(secretKey, PUBLIC_KEY_BYTES, SECRET_KEY_BYTES), secretKey);
        }

        @Override
        public byte[] publicKey() {
            return publicKey.clone();
        }

        @Override
        public byte[] secretKey() {
            return secretKey.clone();
        }

        @Override
        public String toString() {
            return "KeyPair[publicKey=" + java.util.HexFormat.of().formatHex(publicKey) + ", secretKey=<redacted>]";
        }
    }

    private static MethodHandle bind(Linker linker, SymbolLookup lib, String name, FunctionDescriptor fd) {
        MemorySegment symbol = lib.find(name)
                .orElseThrow(() -> new IllegalStateException("symbol not found in libsodium: " + name));
        return linker.downcallHandle(symbol, fd);
    }

    private static MemorySegment copyIn(Arena arena, byte[] bytes) {
        // allocateFrom(JAVA_BYTE, new byte[0]) is legal: a zero-length segment with a valid address.
        return arena.allocateFrom(JAVA_BYTE, bytes);
    }

    private static void check(String fn, int rc) {
        if (rc != 0) {
            throw new IllegalStateException(fn + " returned " + rc);
        }
    }

    private static void requireLength(byte[] bytes, int expected, String what) {
        Objects.requireNonNull(bytes, what);
        if (bytes.length != expected) {
            throw new IllegalArgumentException(what + " must be " + expected + " bytes, got " + bytes.length);
        }
    }

    private static RuntimeException rethrow(Throwable t) {
        if (t instanceof RuntimeException re) {
            return re;
        }
        if (t instanceof Error e) {
            throw e;
        }
        return new IllegalStateException(t);
    }
}
