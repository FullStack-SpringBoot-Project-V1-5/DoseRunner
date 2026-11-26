package tn.rnu.eniso.doserunner.commons.sec;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.util.List;

/**
 * {@code doserunner.security.*}: how a service validates the JWTs issued by identity-service.
 *
 * @param jwksUri     where to fetch identity-service's public keys,
 *                    e.g. {@code http://identity-service:8081/.well-known/jwks.json}.
 *                    Required unless the service defines its own {@code JwtDecoder} bean (identity-service, tests).
 * @param issuer      expected {@code iss} claim
 * @param publicPaths extra paths reachable without a token, added to {@link #DEFAULT_PUBLIC_PATHS}
 */
@ConfigurationProperties("doserunner.security")
public record DoseRunnerSecurityProperties(
        String jwksUri,
        @DefaultValue(DEFAULT_ISSUER) String issuer,
        @DefaultValue List<String> publicPaths) {

    public static final String DEFAULT_ISSUER = "doserunner-identity";

    /** Always public: probes (k8s / compose healthchecks), Boot's error page, and JWKS discovery. */
    public static final List<String> DEFAULT_PUBLIC_PATHS =
            List.of("/actuator/health/**", "/actuator/info", "/error", "/.well-known/**");

    public DoseRunnerSecurityProperties {
        publicPaths = publicPaths == null ? List.of() : List.copyOf(publicPaths);
    }

    /** {@link #DEFAULT_PUBLIC_PATHS} + {@link #publicPaths()}. */
    public String[] allPublicPaths() {
        return java.util.stream.Stream.concat(DEFAULT_PUBLIC_PATHS.stream(), publicPaths.stream())
                .distinct().toArray(String[]::new);
    }
}
