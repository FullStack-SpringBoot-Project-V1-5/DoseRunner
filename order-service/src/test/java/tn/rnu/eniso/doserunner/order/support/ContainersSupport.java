package tn.rnu.eniso.doserunner.order.support;

import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.activemq.ArtemisContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;

public abstract class ContainersSupport {

    @ServiceConnection
    protected static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16.16");

    @ServiceConnection
    protected static final ArtemisContainer ARTEMIS = new ArtemisContainer("apache/activemq-artemis:2.44.0");

    static {
        POSTGRES.start();
        ARTEMIS.start();
    }
}
