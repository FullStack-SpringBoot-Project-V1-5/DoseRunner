package tn.rnu.eniso.doserunner.custody.bo;

import tn.rnu.eniso.doserunner.nativelib.Sodium;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;

/**
 * The hash chain rules (pure business logic: no Spring, no JPA). Crypto goes through libsodium via FFM.
 *
 * <pre>
 *   signature(n) = Ed25519_sign(sk, LinkCodec.signedBytes(n))          signedBytes includes prevHash(n)
 *   linkHash(n)  = BLAKE2b-256(signedBytes(n) || signature(n))
 *   prevHash(n)  = linkHash(n - 1)        prevHash(1) = 32 zero bytes (GENESIS)
 * </pre>
 * Changing any byte of link k breaks its signature; rewriting and re-signing it needs the secret key, and still
 * changes linkHash(k), which breaks prevHash(k + 1).
 */
public final class CustodyChain {

    private static final byte[] GENESIS = new byte[Sodium.HASH_BYTES];

    private final Sodium sodium;
    private final Sodium.KeyPair signingKey;

    public CustodyChain(Sodium sodium, Sodium.KeyPair signingKey) {
        this.sodium = Objects.requireNonNull(sodium, "sodium");
        this.signingKey = Objects.requireNonNull(signingKey, "signingKey");
    }

    /** prevHash of the first link of every chain. */
    public static byte[] genesis() {
        return GENESIS.clone();
    }

    /** What arrives from an event, before it is chained and signed. */
    public record Draft(String eventId, String orderId, LinkType type, String actorId, String robotId,
                        byte[] payload, Instant ts) {
        public Draft {
            Objects.requireNonNull(eventId, "eventId");
            Objects.requireNonNull(orderId, "orderId");
            Objects.requireNonNull(type, "type");
            Objects.requireNonNull(payload, "payload");
            Objects.requireNonNull(ts, "ts");
        }
    }

    /**
     * Builds and signs the link that follows {@code last}.
     *
     * @param last current head of the order's chain, or {@code null} if the order has no link yet
     */
    public Link append(Link last, Draft draft) {
        if (last != null && !last.orderId().equals(draft.orderId())) {
            throw new IllegalArgumentException("last link belongs to order " + last.orderId()
                    + ", draft to " + draft.orderId());
        }
        long seq = last == null ? 1 : last.seq() + 1;
        byte[] prevHash = last == null ? genesis() : linkHash(last);
        Link unsigned = new Link(draft.eventId(), draft.orderId(), seq, draft.type(), draft.actorId(),
                draft.robotId(), sodium.hash(draft.payload()), prevHash, new byte[0],
                draft.ts().truncatedTo(ChronoUnit.MILLIS));
        byte[] signature = sodium.sign(LinkCodec.signedBytes(unsigned), signingKey.secretKey());
        return new Link(unsigned.eventId(), unsigned.orderId(), seq, unsigned.type(), unsigned.actorId(),
                unsigned.robotId(), unsigned.payloadHash(), prevHash, signature, unsigned.ts());
    }

    /** BLAKE2b-256 over the signed bytes and the signature: what the next link stores as prevHash. */
    public byte[] linkHash(Link link) {
        byte[] signed = LinkCodec.signedBytes(link);
        byte[] all = Arrays.copyOf(signed, signed.length + link.signature().length);
        System.arraycopy(link.signature(), 0, all, signed.length, link.signature().length);
        return sodium.hash(all);
    }

    /**
     * Recomputes the whole chain and stops at the first broken link.
     *
     * @param links the order's links in ascending {@code seq} order, as read from storage
     */
    public Verification verify(String orderId, List<Link> links) {
        if (links.isEmpty()) {
            return Verification.broken(0, null, "no custody link for this order");
        }
        byte[] publicKey = signingKey.publicKey();
        byte[] expectedPrev = genesis();
        long expectedSeq = 1;
        for (Link link : links) {
            if (link.seq() != expectedSeq) {
                return Verification.broken(links.size(), link.seq(),
                        "expected seq " + expectedSeq + " but found " + link.seq() + ": link(s) missing or reordered");
            }
            if (!link.orderId().equals(orderId)) {
                return Verification.broken(links.size(), link.seq(), "link belongs to order " + link.orderId());
            }
            if (!Arrays.equals(link.prevHash(), expectedPrev)) {
                return Verification.broken(links.size(), link.seq(),
                        "prevHash does not match the hash of link " + (expectedSeq - 1)
                                + (expectedSeq == 1 ? " (genesis)" : ""));
            }
            if (!sodium.verify(link.signature(), LinkCodec.signedBytes(link), publicKey)) {
                return Verification.broken(links.size(), link.seq(), "Ed25519 signature does not match the link content");
            }
            expectedPrev = linkHash(link);
            expectedSeq++;
        }
        return Verification.ok(links.size());
    }

    public byte[] publicKey() {
        return signingKey.publicKey();
    }

    /** Short, non-secret identifier of the signing key, for logs and the audit page. */
    public String keyFingerprint() {
        return HexFormat.of().formatHex(sodium.hash(signingKey.publicKey()), 0, 8);
    }

    /**
     * @param brokenAtSeq seq of the first link that fails, {@code null} when valid or when there is no link at all
     */
    public record Verification(boolean valid, int links, Long brokenAtSeq, String reason) {

        static Verification ok(int links) {
            return new Verification(true, links, null, null);
        }

        static Verification broken(int links, Long brokenAtSeq, String reason) {
            return new Verification(false, links, brokenAtSeq, reason);
        }
    }
}
