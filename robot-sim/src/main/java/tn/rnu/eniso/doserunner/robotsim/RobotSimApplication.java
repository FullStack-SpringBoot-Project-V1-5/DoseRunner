package tn.rnu.eniso.doserunner.robotsim;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** Pure-Java fallback robot: consumes MissionCommand, publishes RobotTelemetry. Owner: C (Artemis). */
@SpringBootApplication
public class RobotSimApplication {

    public static void main(String[] args) {
        SpringApplication.run(RobotSimApplication.class, args);
    }
}
