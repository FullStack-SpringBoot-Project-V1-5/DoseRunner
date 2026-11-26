package tn.rnu.eniso.doserunner.custody.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.nio.file.Path;

/**
 * {@code custody.signing.*}
 *
 * @param keyFile           file holding the Ed25519 secret key (libsodium's 64 bytes, base64, one line).
 *                          Gitignored by default ({@code secrets/}); in Docker it lives on the {@code custody-keys} volume.
 * @param generateIfMissing dev convenience: create a fresh key on first start. Turn off where a lost key file must
 *                          stop the service instead of silently starting a new key (old chains would stop verifying).
 */
@ConfigurationProperties("custody.signing")
public record SigningProperties(
        @DefaultValue("secrets/custody-ed25519.sk") Path keyFile,
        @DefaultValue("true") boolean generateIfMissing) {
}
