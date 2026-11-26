package tn.rnu.eniso.doserunner.dispatch;

import java.time.Duration;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.MountableFile;

final class ArtemisTestBroker {
    static GenericContainer<?> create() {
        return new GenericContainer<>("apache/activemq-artemis:2.44.0")
            .withEnv("ARTEMIS_USER", "c004")
            .withEnv("ARTEMIS_PASSWORD", "c004-test-password")
            .withEnv("ANONYMOUS_LOGIN", "false")
            .withExposedPorts(61616)
            .withCopyFileToContainer(MountableFile.forHostPath(System.getProperty("broker.configuration")),
                    "/var/lib/artemis-instance/etc-override/broker.xml")
            .withCopyFileToContainer(MountableFile.forHostPath(System.getProperty("broker.startup")),
                    "/opt/doserunner/start-broker.sh")
            .withCreateContainerCmdModifier(cmd -> cmd.withEntrypoint("/bin/bash", "/opt/doserunner/start-broker.sh"))
            .withCommand("run")
            .waitingFor(Wait.forLogMessage(".*AMQ221007: Server is now active.*", 1))
            .withStartupTimeout(Duration.ofSeconds(90));
    }
    private ArtemisTestBroker() { }
}
