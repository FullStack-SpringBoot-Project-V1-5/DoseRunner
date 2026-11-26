package tn.rnu.eniso.doserunner.identity;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Full service on a random port + MockMvc + Testcontainers Postgres + an ephemeral JWT key.
 * Every integration test uses this one annotation, so Spring caches a single context (and container) for all.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "doserunner.jwt.ephemeral-key=true")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
public @interface IdentityIntegrationTest {
}
