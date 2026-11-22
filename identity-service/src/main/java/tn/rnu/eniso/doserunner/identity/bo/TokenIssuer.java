package tn.rnu.eniso.doserunner.identity.bo;

import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import tn.rnu.eniso.doserunner.commons.sec.RolesClaim;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;

/**
 * Builds and signs the RS256 access token. Claim layout (read by every service through commons/sec):
 * {@code iss}, {@code sub} = user id, {@code preferred_username}, {@code roles} = ["NURSE", ...], {@code iat},
 * {@code exp}; header {@code kid} = RFC 7638 thumbprint of the signing key.
 */
public class TokenIssuer {

    private final JwtEncoder encoder;
    private final String keyId;
    private final String issuer;
    private final Duration ttl;
    private final Clock clock;

    public TokenIssuer(JwtEncoder encoder, String keyId, String issuer, Duration ttl, Clock clock) {
        this.encoder = encoder;
        this.keyId = keyId;
        this.issuer = issuer;
        this.ttl = ttl;
        this.clock = clock;
    }

    public IssuedToken issue(String userId, String username, Collection<String> roles) {
        Instant now = clock.instant();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(issuer)
                .subject(userId)
                .issuedAt(now)
                .expiresAt(now.plus(ttl))
                .claim(RolesClaim.USERNAME, username)
                .claim(RolesClaim.ROLES, roles.stream().sorted().toList())
                .build();
        JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256).keyId(keyId).build();
        String value = encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        return new IssuedToken(value, ttl);
    }

    public record IssuedToken(String value, Duration expiresIn) {
    }
}
