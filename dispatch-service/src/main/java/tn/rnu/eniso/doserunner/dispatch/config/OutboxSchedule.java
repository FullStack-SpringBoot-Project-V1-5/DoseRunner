package tn.rnu.eniso.doserunner.dispatch.config;

import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import tn.rnu.eniso.doserunner.dispatch.facade.OutboxRelay;

@Component
@ConditionalOnProperty(name = "dispatch.messaging.relay-enabled", havingValue = "true", matchIfMissing = true)
public class OutboxSchedule {
    private final OutboxRelay relay;
    public OutboxSchedule(OutboxRelay relay) { this.relay = relay; }
    @Scheduled(fixedDelayString = "${dispatch.messaging.relay-delay-ms:1000}")
    public void publishPending() {
        try { relay.relayNext(); }
        catch (RuntimeException error) {
            LoggerFactory.getLogger(OutboxSchedule.class).error("Outbox publication failed; pending record retained for retry", error);
        }
    }
}
