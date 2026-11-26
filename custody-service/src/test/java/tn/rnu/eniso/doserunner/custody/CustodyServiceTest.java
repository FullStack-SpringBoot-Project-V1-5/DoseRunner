package tn.rnu.eniso.doserunner.custody;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Boots the whole custody-service on a random port against Testcontainers Postgres and the real libsodium.
 * All tests using it share one Spring context, hence one database container.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "custody.signing.key-file=target/test-secrets/custody-ed25519.sk")
@Import(PostgresTestcontainer.class)
public @interface CustodyServiceTest {
}
