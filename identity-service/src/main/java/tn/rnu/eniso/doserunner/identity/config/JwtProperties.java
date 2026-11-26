package tn.rnu.eniso.doserunner.identity.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.core.io.Resource;
import tn.rnu.eniso.doserunner.commons.sec.DoseRunnerSecurityProperties;

import java.time.Duration;

/**
 * {@code doserunner.jwt.*}
 *
 * @param privateKeyLocation PKCS#8 PEM RSA key ({@code deploy/compose/gen-jwt-key.sh}); never committed.
 *                           Env var: {@code DOSERUNNER_JWT_PRIVATEKEYLOCATION}.
 * @param ephemeralKey       generate a throwaway key at startup instead (tests, quick local runs).
 *                           Tokens then die with the process, and replicas would not share a key.
 * @param ttl                access-token lifetime
 * @param issuer             {@code iss} claim, must match the services' {@code doserunner.security.issuer}
 */
@ConfigurationProperties("doserunner.jwt")
public record JwtProperties(
        @DefaultValue("file:./secrets/jwt-private.pem") Resource privateKeyLocation,
        @DefaultValue("false") boolean ephemeralKey,
        @DefaultValue("15m") Duration ttl,
        @DefaultValue(DoseRunnerSecurityProperties.DEFAULT_ISSUER) String issuer) {
}
