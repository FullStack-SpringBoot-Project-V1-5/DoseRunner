package tn.rnu.eniso.doserunner.commons.sec;

import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;

/**
 * The JWT claims shared by identity-service (which writes them) and every resource server (which reads them).
 * A token with {@code "roles": ["NURSE"]} gets the authority {@code ROLE_NURSE}, so
 * {@code @PreAuthorize("hasRole('NURSE')")} works.
 */
public final class RolesClaim {

    /** Claim holding the role names, without prefix: {@code ["NURSE", "ADMIN"]}. */
    public static final String ROLES = "roles";
    /** Human-readable login name; {@code sub} holds the user id (UUID). */
    public static final String USERNAME = "preferred_username";

    private RolesClaim() {
    }

    public static JwtAuthenticationConverter authenticationConverter() {
        JwtGrantedAuthoritiesConverter authorities = new JwtGrantedAuthoritiesConverter();
        authorities.setAuthoritiesClaimName(ROLES);
        authorities.setAuthorityPrefix("ROLE_");
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authorities);
        return converter;
    }
}
