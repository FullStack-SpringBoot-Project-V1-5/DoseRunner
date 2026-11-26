package tn.rnu.eniso.doserunner.custody.pres;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import tn.rnu.eniso.doserunner.custody.bo.LinkType;
import tn.rnu.eniso.doserunner.custody.facade.CustodyFacade;
import tn.rnu.eniso.doserunner.custody.facade.RecordCommand;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

/**
 * Profile {@code demo} only: records the happy path of order {@code ORD-DEMO-1} so the audit page has something to
 * show before the Artemis listener exists. Idempotent (fixed event ids). Stands in for the future event adapter,
 * hence it lives in {@code pres} and goes through the facade.
 */
@Component
@Profile("demo")
class DemoChainSeeder implements ApplicationRunner {

    static final String ORDER = "ORD-DEMO-1";

    private final CustodyFacade facade;

    DemoChainSeeder(CustodyFacade facade) {
        this.facade = facade;
    }

    @Override
    public void run(ApplicationArguments args) {
        Instant t0 = Instant.parse("2026-11-20T08:00:00Z");
        record("demo-1", LinkType.ORDER_PLACED, "nurse-07", null, t0);
        record("demo-2", LinkType.ORDER_VALIDATED, "pharm-02", null, t0.plusSeconds(240));
        record("demo-3", LinkType.ROBOT_ASSIGNED, "dispatch-service", "R-01", t0.plusSeconds(250));
        record("demo-4", LinkType.PICKUP_CONFIRMED, "pharm-02", "R-01", t0.plusSeconds(420));
        record("demo-5", LinkType.DELIVERY_CONFIRMED, "nurse-07", "R-01", t0.plusSeconds(780));
    }

    private void record(String eventId, LinkType type, String actor, String robot, Instant ts) {
        byte[] payload = (eventId + "|" + type + "|" + ORDER).getBytes(StandardCharsets.UTF_8);
        facade.record(new RecordCommand(eventId, ORDER, type, actor, robot, payload, ts));
    }
}
