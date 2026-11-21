package tn.rnu.eniso.doserunner.dispatch.facade;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import tn.rnu.eniso.doserunner.dispatch.dao.DispatchMessageStore;
import tn.rnu.eniso.doserunner.dispatch.messaging.DispatchEventPublisher;

@Service
public class OutboxRelay {
    private final DispatchMessageStore store;
    private final DispatchEventPublisher publisher;
    public OutboxRelay(DispatchMessageStore store, DispatchEventPublisher publisher) {
        this.store = store; this.publisher = publisher;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public boolean relayNext() {
        store.lock();
        var pending = store.nextPending();
        if (pending.isEmpty()) return false;
        var publication = pending.getFirst();
        publisher.publish(publication);
        // A crash before this DB commit retries the same payload/duplicate IDs after restart.
        store.markSent(publication.id());
        return true;
    }
}
