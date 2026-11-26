package tn.rnu.eniso.doserunner.custody.facade;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import tn.rnu.eniso.doserunner.custody.CustodyServiceTest;
import tn.rnu.eniso.doserunner.custody.bo.LinkType;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Facade + JPA + Flyway schema + real Postgres + real libsodium. Tampering is done the way an insider would:
 * plain SQL on the table, after disabling the append-only trigger.
 */
@CustodyServiceTest
class CustodyFacadeTest {

    @Autowired
    CustodyFacade facade;

    @Autowired
    JdbcTemplate jdbc;

    private static final Instant T0 = Instant.parse("2026-11-20T08:00:00.123456Z");

    /** Records placed â†’ pickup â†’ delivery for a fresh order id and returns that id. */
    private String recordHappyPath() {
        String order = "ORD-" + UUID.randomUUID();
        record(order, LinkType.ORDER_PLACED, "nurse-07", null, T0);
        record(order, LinkType.PICKUP_CONFIRMED, "pharm-02", "R-01", T0.plusSeconds(300));
        record(order, LinkType.DELIVERY_CONFIRMED, "nurse-07", "R-01", T0.plusSeconds(900));
        return order;
    }

    private CustodyLinkView record(String order, LinkType type, String actor, String robot, Instant ts) {
        String eventId = UUID.randomUUID().toString();
        return facade.record(new RecordCommand(eventId, order, type, actor, robot,
                ("event " + eventId).getBytes(StandardCharsets.UTF_8), ts));
    }

    private void tamper(String sql, Object... args) {
        jdbc.execute("ALTER TABLE custody_event DISABLE TRIGGER custody_event_no_update_delete");
        try {
            jdbc.update(sql, args);
        } finally {
            jdbc.execute("ALTER TABLE custody_event ENABLE TRIGGER custody_event_no_update_delete");
        }
    }

    @Test
    void recordedChainIsReadBackInOrderAndVerifies() {
        String order = recordHappyPath();

        List<CustodyLinkView> chain = facade.chain(order);

        assertThat(chain).extracting(CustodyLinkView::seq).containsExactly(1L, 2L, 3L);
        assertThat(chain.getFirst().prevHash()).isEqualTo("0".repeat(64));
        assertThat(chain.getFirst().ts()).isEqualTo(Instant.parse("2026-11-20T08:00:00.123Z"));
        assertThat(facade.verify(order)).isEqualTo(new VerificationReport(order, true, 3, null, null));
    }

    @Test
    void sameEventRecordedTwiceCreatesOneLink() {
        String order = "ORD-" + UUID.randomUUID();
        RecordCommand cmd = new RecordCommand("evt-" + order, order, LinkType.ORDER_PLACED, "nurse-07", null,
                new byte[]{1, 2, 3}, T0);

        CustodyLinkView first = facade.record(cmd);
        CustodyLinkView again = facade.record(cmd);

        assertThat(again).isEqualTo(first);
        assertThat(facade.chain(order)).hasSize(1);
    }

    @Test
    void appendOnlyTriggerRefusesUpdateAndDelete() {
        String order = recordHappyPath();

        assertThatThrownBy(() -> jdbc.update("UPDATE custody_event SET actor_id = 'x' WHERE order_id = ?", order))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("append-only");
        assertThatThrownBy(() -> jdbc.update("DELETE FROM custody_event WHERE order_id = ?", order))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("append-only");
    }

    @Test
    void changedActorInTheDatabaseIsReportedAtThatLink() {
        String order = recordHappyPath();

        tamper("UPDATE custody_event SET actor_id = 'pharm-99' WHERE order_id = ? AND seq = 2", order);

        VerificationReport report = facade.verify(order);
        assertThat(report.valid()).isFalse();
        assertThat(report.brokenAtSeq()).isEqualTo(2L);
        assertThat(report.reason()).contains("signature");
    }

    @Test
    void oneFlippedBitOfAStoredHashIsDetected() {
        String order = recordHappyPath();

        tamper("UPDATE custody_event SET payload_hash = set_byte(payload_hash, 0, get_byte(payload_hash, 0) # 1) "
                + "WHERE order_id = ? AND seq = 1", order);

        assertThat(facade.verify(order).brokenAtSeq()).isEqualTo(1L);
    }

    @Test
    void deletedMiddleLinkIsDetected() {
        String order = recordHappyPath();

        tamper("DELETE FROM custody_event WHERE order_id = ? AND seq = 2", order);

        VerificationReport report = facade.verify(order);
        assertThat(report.brokenAtSeq()).isEqualTo(3L);
        assertThat(report.reason()).contains("expected seq 2");
    }

    @Test
    void newLinkAfterReloadChainsOntoTheStoredHead() {
        String order = recordHappyPath();

        record(order, LinkType.MISSION_ABORTED, "dispatch-service", "R-01", T0.plusSeconds(1000));

        assertThat(facade.verify(order)).isEqualTo(new VerificationReport(order, true, 4, null, null));
    }

    /**
     * Known limitation, kept as a test so nobody claims otherwise: removing the newest link(s) leaves a shorter
     * chain that still verifies. Detecting it needs the head hash anchored outside this database.
     */
    @Test
    void knownLimitationDeletingTheTailIsNotDetected() {
        String order = recordHappyPath();

        tamper("DELETE FROM custody_event WHERE order_id = ? AND seq = 3", order);

        assertThat(facade.verify(order)).isEqualTo(new VerificationReport(order, true, 2, null, null));
    }

    @Test
    void unknownOrderHasNoLinks() {
        VerificationReport report = facade.verify("ORD-does-not-exist");

        assertThat(report.valid()).isFalse();
        assertThat(report.links()).isZero();
        assertThat(report.brokenAtSeq()).isNull();
    }
}
