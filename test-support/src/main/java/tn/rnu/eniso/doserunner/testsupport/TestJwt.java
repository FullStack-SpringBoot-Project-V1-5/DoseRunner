package tn.rnu.eniso.doserunner.testsupport;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import tn.rnu.eniso.doserunner.commons.sec.DoseRunnerSecurityProperties;
import tn.rnu.eniso.doserunner.commons.sec.RolesClaim;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Signs JWTs shaped exactly like identity-service's tokens, with a throwaway RSA key generated once per JVM.
 * A service test can then check 401 / 403 / 2xx without identity-service running:
 * <pre>{@code
 * @SpringBootTest
 * @AutoConfigureMockMvc
 * @Import(TestJwtConfiguration.class)          // replaces the JWKS-based JwtDecoder
 * class OrderSecurityTest {
 *     @Test void nurseCanOrder() throws Exception {
 *         mvc.perform(post("/orders").header("Authorization", TestJwt.bearer("NURSE")) ...
 * }</pre>
 */
public final class TestJwt {

    private static final RSAKey KEY = generate();
    private static final NimbusJwtEncoder ENCODER = new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(KEY)));

    private TestJwt() {
    }

    /** A valid 15-minute token for a random user holding these roles (e.g. {@code "NURSE"}). */
    public static String forRoles(String... roles) {
        return token(UUID.randomUUID().toString(), "test-user", Duration.ofMinutes(15), roles);
    }

    /** {@code "Bearer " + forRoles(roles)}, ready for an {@code Authorization} header. */
    public static String bearer(String... roles) {
        return "Bearer " + forRoles(roles);
    }

    /** A token that expired one hour ago: the resource server must answer 401. */
    public static String expired(String... roles) {
        return token(UUID.randomUUID().toString(), "test-user", Duration.ofHours(-1), roles);
    }

    /** Full control over subject (user id), username and lifetime. A negative lifetime gives an expired token. */
    public static String token(String subject, String username, Duration lifetime, String... roles) {
        Instant now = Instant.now();
        Instant issuedAt = lifetime.isNegative() ? now.plus(lifetime).minus(Duration.ofMinutes(15)) : now;
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(DoseRunnerSecurityProperties.DEFAULT_ISSUER)
                .subject(subject)
                .issuedAt(issuedAt)
                .expiresAt(now.plus(lifetime))
                .claim(RolesClaim.USERNAME, username)
                .claim(RolesClaim.ROLES, List.of(roles))
                .build();
        JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256).keyId(KEY.getKeyID()).build();
        return ENCODER.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    /** Validates tokens from this class (signature + expiry + issuer), like the production decoder. */
    public static JwtDecoder decoder() {
        try {
            NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(KEY.toRSAPublicKey()).build();
            decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(DoseRunnerSecurityProperties.DEFAULT_ISSUER));
            return decoder;
        } catch (com.nimbusds.jose.JOSEException e) {
            throw new IllegalStateException(e);
        }
    }

    /** The public JWKS, as identity-service would serve it at {@code /.well-known/jwks.json}. */
    public static String publicJwks() {
        return new JWKSet(KEY).toPublicJWKSet().toString();
    }

    private static RSAKey generate() {
        try {
            return new RSAKeyGenerator(2048).keyUse(KeyUse.SIGNATURE).keyIDFromThumbprint(true).generate();
        } catch (com.nimbusds.jose.JOSEException e) {
            throw new IllegalStateException("Cannot generate the test RSA key", e);
        }
    }
}
