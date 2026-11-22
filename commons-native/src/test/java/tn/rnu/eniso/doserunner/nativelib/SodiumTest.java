package tn.rnu.eniso.doserunner.nativelib;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Real calls into libsodium (no mocks). Skipped when libsodium is not installed, unless
 * {@code DOSERUNNER_NATIVE_REQUIRED=true} (CI and the Docker build set it), in which case a missing library fails.
 */
class SodiumTest {

    static Sodium sodium;

    @BeforeAll
    static void load() {
        try {
            sodium = Sodium.get();
        } catch (IllegalArgumentException missingLibrary) {
            if ("true".equals(System.getenv("DOSERUNNER_NATIVE_REQUIRED"))) {
                throw missingLibrary;
            }
            assumeTrue(false, "libsodium not installed: " + missingLibrary.getMessage());
        }
    }

    private static final byte[] MESSAGE = "order ORD-42 handed to robot R-01".getBytes(StandardCharsets.UTF_8);

    @Test
    void signThenVerifyIsOk() {
        Sodium.KeyPair kp = sodium.generateKeyPair();

        byte[] sig = sodium.sign(MESSAGE, kp.secretKey());

        assertThat(sig).hasSize(Sodium.SIGNATURE_BYTES);
        assertThat(sodium.verify(sig, MESSAGE, kp.publicKey())).isTrue();
    }

    @Test
    void flippingOneByteOfTheMessageFailsVerify() {
        Sodium.KeyPair kp = sodium.generateKeyPair();
        byte[] sig = sodium.sign(MESSAGE, kp.secretKey());

        byte[] tampered = MESSAGE.clone();
        tampered[7] ^= 0x01;

        assertThat(sodium.verify(sig, tampered, kp.publicKey())).isFalse();
    }

    @Test
    void flippingOneByteOfTheSignatureFailsVerify() {
        Sodium.KeyPair kp = sodium.generateKeyPair();
        byte[] sig = sodium.sign(MESSAGE, kp.secretKey());

        sig[0] ^= 0x01;

        assertThat(sodium.verify(sig, MESSAGE, kp.publicKey())).isFalse();
    }

    @Test
    void anotherKeyFailsVerify() {
        byte[] sig = sodium.sign(MESSAGE, sodium.generateKeyPair().secretKey());

        assertThat(sodium.verify(sig, MESSAGE, sodium.generateKeyPair().publicKey())).isFalse();
    }

    @Test
    void wrongLengthSignatureOrKeyIsRejectedNotPassedToC() {
        Sodium.KeyPair kp = sodium.generateKeyPair();
        byte[] sig = sodium.sign(MESSAGE, kp.secretKey());

        assertThat(sodium.verify(new byte[10], MESSAGE, kp.publicKey())).isFalse();
        assertThat(sodium.verify(sig, MESSAGE, new byte[10])).isFalse();
        assertThatThrownBy(() -> sodium.sign(MESSAGE, new byte[32])).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void emptyMessageCanBeSignedAndVerified() {
        Sodium.KeyPair kp = sodium.generateKeyPair();
        byte[] sig = sodium.sign(new byte[0], kp.secretKey());

        assertThat(sodium.verify(sig, new byte[0], kp.publicKey())).isTrue();
    }

    @Test
    void generichashMatchesBlake2b256KnownAnswer() {
        // BLAKE2b-256("abc"), RFC 7693 test vector family; any byte change flips the digest.
        assertThat(HexFormat.of().formatHex(sodium.hash("abc".getBytes(StandardCharsets.US_ASCII))))
                .isEqualTo("bddd813c634239723171ef3fee98579b94964e3bb1cb3e427262c8c068d52319");
        assertThat(sodium.hash(new byte[0])).hasSize(Sodium.HASH_BYTES);
    }

    @Test
    void keyPairRebuiltFromSecretKeyStillVerifies() {
        Sodium.KeyPair kp = sodium.generateKeyPair();
        Sodium.KeyPair reloaded = Sodium.KeyPair.fromSecretKey(kp.secretKey());

        byte[] sig = sodium.sign(MESSAGE, reloaded.secretKey());

        assertThat(reloaded.publicKey()).isEqualTo(kp.publicKey());
        assertThat(sodium.verify(sig, MESSAGE, kp.publicKey())).isTrue();
        assertThat(reloaded.toString()).contains("<redacted>");
    }
}
