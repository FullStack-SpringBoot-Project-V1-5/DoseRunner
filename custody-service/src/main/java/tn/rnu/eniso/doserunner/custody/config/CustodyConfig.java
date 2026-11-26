package tn.rnu.eniso.doserunner.custody.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tn.rnu.eniso.doserunner.custody.bo.CustodyChain;
import tn.rnu.eniso.doserunner.nativelib.Sodium;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Base64;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(SigningProperties.class)
public class CustodyConfig {

    private static final Logger log = LoggerFactory.getLogger(CustodyConfig.class);

    /** Loads libsodium through FFM; fails fast at startup if the native library is missing. */
    @Bean
    Sodium sodium() {
        return Sodium.get();
    }

    @Bean
    CustodyChain custodyChain(Sodium sodium, SigningProperties props) {
        CustodyChain chain = new CustodyChain(sodium, loadOrCreateKey(sodium, props));
        log.info("custody signing key ready, fingerprint={}", chain.keyFingerprint());
        return chain;
    }

    static Sodium.KeyPair loadOrCreateKey(Sodium sodium, SigningProperties props) {
        Path file = props.keyFile().toAbsolutePath();
        try {
            if (Files.exists(file)) {
                byte[] sk = Base64.getDecoder().decode(Files.readString(file, StandardCharsets.US_ASCII).strip());
                return Sodium.KeyPair.fromSecretKey(sk);
            }
            if (!props.generateIfMissing()) {
                throw new IllegalStateException("custody signing key not found: " + file
                        + " (custody.signing.generate-if-missing=false)");
            }
            Sodium.KeyPair kp = sodium.generateKeyPair();
            writeOwnerOnly(file, Base64.getEncoder().encodeToString(kp.secretKey()));
            log.warn("custody signing key not found: generated a NEW Ed25519 key at {}. "
                    + "Chains signed with a previous key will no longer verify.", file);
            return kp;
        } catch (FileAlreadyExistsException race) {
            return loadOrCreateKey(sodium, props); // another instance created it first: use that one
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read/write custody signing key " + file, e);
        }
    }

    private static void writeOwnerOnly(Path file, String content) throws IOException {
        Files.createDirectories(file.getParent());
        byte[] bytes = (content + "\n").getBytes(StandardCharsets.US_ASCII);
        if (file.getFileSystem().supportedFileAttributeViews().contains("posix")) {
            Files.createFile(file, PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));
            Files.write(file, bytes, StandardOpenOption.TRUNCATE_EXISTING);
        } else {
            Files.write(file, bytes, StandardOpenOption.CREATE_NEW);
        }
    }
}
