package tn.rnu.eniso.doserunner.order.support;

import org.springframework.test.web.servlet.request.RequestPostProcessor;
import tn.rnu.eniso.doserunner.testsupport.TestJwt;

import java.time.Duration;

/**
 * Bearer tokens for MockMvc requests, signed by {@link TestJwt} (the test classes import {@code TestJwtConfiguration}
 * through {@link ContainersSupport}). The subject becomes the actor id: {@code nurse("u-nurse-1")} places an order
 * whose {@code nurseId} is {@code u-nurse-1}.
 */
public final class AuthTokens {

    private AuthTokens() {
    }

    public static RequestPostProcessor nurse() {
        return nurse("u-nurse-test");
    }

    public static RequestPostProcessor nurse(String userId) {
        return as(userId, "NURSE");
    }

    public static RequestPostProcessor pharmacist() {
        return pharmacist("u-pharma-test");
    }

    public static RequestPostProcessor pharmacist(String userId) {
        return as(userId, "PHARMACIST");
    }

    public static RequestPostProcessor as(String userId, String... roles) {
        String token = TestJwt.token(userId, userId, Duration.ofMinutes(15), roles);
        return request -> {
            request.addHeader("Authorization", "Bearer " + token);
            return request;
        };
    }
}
