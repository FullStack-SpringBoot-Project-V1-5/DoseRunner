package tn.rnu.eniso.doserunner.custody.dao;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CustodyEventRepository extends JpaRepository<CustodyEvent, Long> {

    List<CustodyEvent> findByOrderIdOrderBySeqAsc(String orderId);

    /** Head of the chain (the link the next one must point to). */
    Optional<CustodyEvent> findFirstByOrderIdOrderBySeqDesc(String orderId);

    Optional<CustodyEvent> findByEventId(String eventId);
}
