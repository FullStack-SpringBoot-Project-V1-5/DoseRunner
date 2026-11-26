package tn.rnu.eniso.doserunner.custody.bo;

import java.time.Instant;
import java.util.Objects;

/**
 * One link of an order's custody chain, as the business layer sees it (no JPA).
 *
 * @param eventId     id of the contracts event this link records (dedup key, ADR 0001 #1)
 * @param seq         1, 2, 3... per order, no gaps
 * @param actorId     human or service that performed the step; {@code null} when there is none
 * @param robotId     robot involved; {@code null} before a robot is assigned
 * @param payloadHash BLAKE2b-256 of the event bytes (the event itself is not stored: no patient data in the ledger)
 * @param prevHash    {@link CustodyChain#linkHash(Link)} of link {@code seq - 1}, or {@link CustodyChain#GENESIS} for seq 1
 * @param signature   Ed25519 over {@link LinkCodec#signedBytes(Link)}
 * @param ts          when the step happened, millisecond precision (what Postgres stores and the signature covers)
 */
public record Link(String eventId, String orderId, long seq, LinkType type, String actorId, String robotId,
                   byte[] payloadHash, byte[] prevHash, byte[] signature, Instant ts) {

    public Link {
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(orderId, "orderId");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(payloadHash, "payloadHash");
        Objects.requireNonNull(prevHash, "prevHash");
        Objects.requireNonNull(signature, "signature");
        Objects.requireNonNull(ts, "ts");
    }
}
