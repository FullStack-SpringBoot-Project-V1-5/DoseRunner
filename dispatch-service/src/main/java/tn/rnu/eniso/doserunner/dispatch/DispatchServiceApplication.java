package tn.rnu.eniso.doserunner.dispatch;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** Robot fleet, missions, DispatchStrategy SPI, gRPC server. Owner: C (Artemis). */
@SpringBootApplication
public class DispatchServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(DispatchServiceApplication.class, args);
    }
}
