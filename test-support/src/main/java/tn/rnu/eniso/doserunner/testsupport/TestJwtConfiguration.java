package tn.rnu.eniso.doserunner.testsupport;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.security.oauth2.jwt.JwtDecoder;

/**
 * {@code @Import(TestJwtConfiguration.class)} in a {@code @SpringBootTest}: the service then accepts {@link TestJwt}
 * tokens instead of fetching identity-service's JWKS (commons' JWKS decoder backs off when this bean exists).
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestJwtConfiguration {

    @Bean
    JwtDecoder jwtDecoder() {
        return TestJwt.decoder();
    }
}
