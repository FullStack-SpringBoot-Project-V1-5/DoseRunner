package tn.rnu.eniso.doserunner.custody.bo;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import tn.rnu.eniso.doserunner.nativelib.Sodium;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** Chain rules with the real libsodium, no database. */
class CustodyChainTest {

    static Sodium sodium;

    @BeforeAll
    static void load() {
        try {
            sodium = Sodium.get();
        } catch (IllegalArgumentException missingLibrary) {
            if ("true".equals(System.getenv("DOSERUNNER_NATIVE_REQUIRED"))) {
                throw missingLibrary;
            }
            assumeTrue(false, "libsodium not installed: " + missingLibrary.getMessage());
        }
    }

    private final CustodyChain chain = new CustodyChain(sodium, sodium.generateKeyPair());
    private static final Instant T0 = Instant.parse("2026-11-20T08:00:00.123456789Z");

    private List<Link> threeLinks() {
        List<Link> links = new ArrayList<>();
        Link l1 = chain.append(null, draft("e1", LinkType.ORDER_PLACED, "nurse-07", null, T0));
        Link l2 = chain.append(l1, draft("e2", LinkType.PICKUP_CONFIRMED, "pharm-02", "R-01", T0.plusSeconds(60)));
        Link l3 = chain.append(l2, draft("e3", LinkType.DELIVERY_CONFIRMED, "nurse-07", "R-01", T0.plusSeconds(600)));
        links.add(l1);
        links.add(l2);
        links.add(l3);
        return links;
    }

    private static CustodyChain.Draft draft(String eventId, LinkType type, String actor, String robot, Instant ts) {
        return new CustodyChain.Draft(eventId, "ORD-1", type, actor, robot,
                (eventId + type).getBytes(StandardCharsets.UTF_8), ts);
    }

    @Test
    void appendedChainVerifies() {
        List<Link> links = threeLinks();

        assertThat(links).extracting(Link::seq).containsExactly(1L, 2L, 3L);
        assertThat(links.getFirst().prevHash()).isEqualTo(CustodyChain.genesis());
        assertThat(links.get(1).prevHash()).isEqualTo(chain.linkHash(links.getFirst()));
        assertThat(links.getFirst().ts()).isEqualTo(Instant.parse("2026-11-20T08:00:00.123Z"));
        assertThat(chain.verify("ORD-1", links))
                .isEqualTo(new CustodyChain.Verification(true, 3, null, null));
    }

    @Test
    void changedFieldBreaksThatLinksSignature() {
        List<Link> links = threeLinks();
        Link l2 = links.get(1);
        links.set(1, new Link(l2.eventId(), l2.orderId(), l2.seq(), l2.type(), "someone-else", l2.robotId(),
                l2.payloadHash(), l2.prevHash(), l2.signature(), l2.ts()));

        CustodyChain.Verification v = chain.verify("ORD-1", links);

        assertThat(v.valid()).isFalse();
        assertThat(v.brokenAtSeq()).isEqualTo(2L);
        assertThat(v.reason()).contains("signature");
    }

    @Test
    void flippedSignatureByteIsDetected() {
        List<Link> links = threeLinks();
        Link l1 = links.getFirst();
        byte[] sig = l1.signature();
        sig[10] ^= 0x01;
        links.set(0, new Link(l1.eventId(), l1.orderId(), 1, l1.type(), l1.actorId(), l1.robotId(),
                l1.payloadHash(), l1.prevHash(), sig, l1.ts()));

        assertThat(chain.verify("ORD-1", links).brokenAtSeq()).isEqualTo(1L);
    }

    @Test
    void linkReSignedByAnotherKeyIsDetected() {
        List<Link> links = threeLinks();
        CustodyChain forger = new CustodyChain(sodium, sodium.generateKeyPair());
        links.set(2, forger.append(links.get(1), draft("e3", LinkType.DELIVERY_CONFIRMED, "intruder", "R-01", T0)));

        CustodyChain.Verification v = chain.verify("ORD-1", links);

        assertThat(v.brokenAtSeq()).isEqualTo(3L);
        assertThat(v.reason()).contains("signature");
    }

    @Test
    void missingMiddleLinkIsDetected() {
        List<Link> links = threeLinks();
        links.remove(1);

        CustodyChain.Verification v = chain.verify("ORD-1", links);

        assertThat(v.brokenAtSeq()).isEqualTo(3L);
        assertThat(v.reason()).contains("expected seq 2");
    }

    @Test
    void wrongPrevHashIsDetected() {
        List<Link> links = threeLinks();
        Link l3 = links.get(2);
        links.set(2, new Link(l3.eventId(), l3.orderId(), 3, l3.type(), l3.actorId(), l3.robotId(),
                l3.payloadHash(), CustodyChain.genesis(), l3.signature(), l3.ts()));

        CustodyChain.Verification v = chain.verify("ORD-1", links);

        assertThat(v.brokenAtSeq()).isEqualTo(3L);
        assertThat(v.reason()).contains("prevHash");
    }

    @Test
    void emptyChainIsNotValid() {
        assertThat(chain.verify("ORD-1", List.of()))
                .isEqualTo(new CustodyChain.Verification(false, 0, null, "no custody link for this order"));
    }

    @Test
    void appendingToAnotherOrdersHeadIsRefused() {
        Link head = chain.append(null, draft("e1", LinkType.ORDER_PLACED, "n", null, T0));
        CustodyChain.Draft other = new CustodyChain.Draft("x", "ORD-2", LinkType.ORDER_PLACED, "n", null,
                new byte[0], T0);

        assertThatThrownBy(() -> chain.append(head, other)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void canonicalEncodingDistinguishesNullFromEmptyAndFieldBoundaries() {
        Link base = threeLinks().getFirst();
        Link nullActor = new Link(base.eventId(), base.orderId(), 1, base.type(), null, "",
                base.payloadHash(), base.prevHash(), base.signature(), base.ts());
        Link emptyActor = new Link(base.eventId(), base.orderId(), 1, base.type(), "", null,
                base.payloadHash(), base.prevHash(), base.signature(), base.ts());

        assertThat(LinkCodec.signedBytes(nullActor)).isNotEqualTo(LinkCodec.signedBytes(emptyActor));
    }
}
