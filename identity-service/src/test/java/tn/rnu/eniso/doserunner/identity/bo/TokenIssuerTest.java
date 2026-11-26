package tn.rnu.eniso.doserunner.identity.bo;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtValidationException;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TokenIssuerTest {

    private static final RSAKey KEY = key();

    @Test
    void tokenCarriesTheAgreedClaimsAndVerifiesAgainstThePublicJwks() throws Exception {
        TokenIssuer issuer = issuer(Clock.systemUTC());
        TokenIssuer.IssuedToken token = issuer.issue("user-1", "nurse", List.of("PHARMACIST", "NURSE"));

        RSAKey publicOnly = new JWKSet(KEY).toPublicJWKSet().getKeys().getFirst().toRSAKey();
        assertThat(publicOnly.isPrivate()).isFalse();
        Jwt jwt = NimbusJwtDecoder.withPublicKey(publicOnly.toRSAPublicKey()).build().decode(token.value());

        assertThat(jwt.getHeaders()).containsEntry("alg", "RS256").containsEntry("kid", KEY.getKeyID());
        assertThat(jwt.getClaimAsString("iss")).isEqualTo("doserunner-identity");
        assertThat(jwt.getSubject()).isEqualTo("user-1");
        assertThat(jwt.getClaimAsString("preferred_username")).isEqualTo("nurse");
        assertThat(jwt.getClaimAsStringList("roles")).containsExactly("NURSE", "PHARMACIST");
        assertThat(Duration.between(jwt.getIssuedAt(), jwt.getExpiresAt())).isEqualTo(Duration.ofMinutes(15));
        assertThat(token.expiresIn()).isEqualTo(Duration.ofMinutes(15));
    }

    @Test
    void anExpiredTokenIsRejected() throws Exception {
        Clock anHourAgo = Clock.fixed(Instant.now().minus(Duration.ofHours(1)), ZoneOffset.UTC);
        String token = issuer(anHourAgo).issue("user-1", "nurse", List.of("NURSE")).value();
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(KEY.toRSAPublicKey()).build();
        assertThatThrownBy(() -> decoder.decode(token)).isInstanceOf(JwtValidationException.class)
                .hasMessageContaining("expired");
    }

    private static TokenIssuer issuer(Clock clock) {
        return new TokenIssuer(new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(KEY))), KEY.getKeyID(),
                "doserunner-identity", Duration.ofMinutes(15), clock);
    }

    private static RSAKey key() {
        try {
            return new RSAKeyGenerator(2048).keyIDFromThumbprint(true).generate();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
