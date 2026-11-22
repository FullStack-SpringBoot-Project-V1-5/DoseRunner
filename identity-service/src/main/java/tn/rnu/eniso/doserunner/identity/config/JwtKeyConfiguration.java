package tn.rnu.eniso.doserunner.identity.config;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.security.converter.RsaKeyConverters;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import tn.rnu.eniso.doserunner.identity.bo.TokenIssuer;

import java.io.IOException;
import java.io.InputStream;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.RSAPublicKeySpec;
import java.time.Clock;

/**
 * The RS256 signing key and everything built from it. identity-service validates its own tokens with the
 * local public key (this {@link JwtDecoder} replaces commons' JWKS-over-HTTP decoder), so it never calls itself.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(JwtProperties.class)
public class JwtKeyConfiguration {

    private static final Logger log = LoggerFactory.getLogger(JwtKeyConfiguration.class);

    @Bean
    RSAKey jwtSigningKey(JwtProperties props) throws IOException, GeneralSecurityException, JOSEException {
        RSAKey key = props.ephemeralKey() ? generate() : load(props.privateKeyLocation());
        log.info("JWT signing key ready: kid={} ({})", key.getKeyID(), props.ephemeralKey() ? "ephemeral" : "file");
        return key;
    }

    @Bean
    JwtEncoder jwtEncoder(RSAKey jwtSigningKey) {
        return new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(jwtSigningKey)));
    }

    @Bean
    JwtDecoder jwtDecoder(RSAKey jwtSigningKey, JwtProperties props) throws JOSEException {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(jwtSigningKey.toRSAPublicKey()).build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(props.issuer()));
        return decoder;
    }

    @Bean
    PublicJwks publicJwks(RSAKey jwtSigningKey) {
        return new PublicJwks(new JWKSet(jwtSigningKey).toPublicJWKSet().toJSONObject());
    }

    @Bean
    TokenIssuer tokenIssuer(JwtEncoder jwtEncoder, RSAKey jwtSigningKey, JwtProperties props) {
        return new TokenIssuer(jwtEncoder, jwtSigningKey.getKeyID(), props.issuer(), props.ttl(), Clock.systemUTC());
    }

    private static RSAKey generate() throws JOSEException {
        log.warn("Using an EPHEMERAL JWT signing key: tokens die with this process. Dev/test only.");
        return new RSAKeyGenerator(2048).keyUse(KeyUse.SIGNATURE).algorithm(JWSAlgorithm.RS256)
                .keyIDFromThumbprint(true).generate();
    }

    private static RSAKey load(Resource location) throws IOException, GeneralSecurityException, JOSEException {
        if (!location.exists()) {
            throw new IllegalStateException("JWT private key not found at " + location.getDescription()
                    + ". Generate one with deploy/compose/gen-jwt-key.sh, point doserunner.jwt.private-key-location"
                    + " at it, or set doserunner.jwt.ephemeral-key=true for a local run.");
        }
        RSAPrivateKey privateKey;
        try (InputStream in = location.getInputStream()) {
            privateKey = RsaKeyConverters.pkcs8().convert(in);
        }
        if (!(privateKey instanceof RSAPrivateCrtKey crt)) {
            throw new IllegalStateException("JWT key must be a PKCS#8 RSA private key with CRT parameters");
        }
        if (crt.getModulus().bitLength() < 2048) {
            throw new IllegalStateException("JWT key must be at least 2048 bits");
        }
        RSAPublicKey publicKey = (RSAPublicKey) KeyFactory.getInstance("RSA")
                .generatePublic(new RSAPublicKeySpec(crt.getModulus(), crt.getPublicExponent()));
        return new RSAKey.Builder(publicKey).privateKey(privateKey).keyUse(KeyUse.SIGNATURE)
                .algorithm(JWSAlgorithm.RS256).keyIDFromThumbprint().build();
    }
}
