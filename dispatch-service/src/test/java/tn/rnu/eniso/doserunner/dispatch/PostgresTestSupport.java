package tn.rnu.eniso.doserunner.dispatch;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers
abstract class PostgresTestSupport {

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16.16");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("dispatch.messaging.listener-enabled", () -> false);
        registry.add("dispatch.messaging.relay-enabled", () -> false);
        registry.add("management.health.jms.enabled", () -> false);
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }
}
