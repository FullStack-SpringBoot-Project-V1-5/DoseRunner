package tn.rnu.eniso.doserunner.identity.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import tn.rnu.eniso.doserunner.identity.bo.CredentialPolicy;

/**
 * identity-service wiring. The API security chain itself comes from commons
 * ({@code DoseRunnerResourceServerAutoConfiguration}); {@code /auth/login} is made public in application.yml.
 */
@Configuration(proxyBeanMethods = false)
public class IdentityConfiguration {

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    CredentialPolicy credentialPolicy() {
        return new CredentialPolicy();
    }
}
