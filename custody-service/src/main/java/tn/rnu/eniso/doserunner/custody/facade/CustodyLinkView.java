package tn.rnu.eniso.doserunner.custody.facade;

import java.time.Instant;

/** A ledger link as exposed by REST and the audit page. Hashes and signature are lowercase hex. */
public record CustodyLinkView(long seq, String type, String eventId, String actorId, String robotId, Instant ts,
                              String payloadHash, String prevHash, String signature) {
}
