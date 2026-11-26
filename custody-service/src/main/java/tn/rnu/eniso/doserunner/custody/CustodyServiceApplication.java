package tn.rnu.eniso.doserunner.custody;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** Signed, hash-chained custody ledger + /verify. Owner: D (FFM). */
@SpringBootApplication
public class CustodyServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(CustodyServiceApplication.class, args);
    }
}
