package tn.rnu.eniso.doserunner.custody.facade;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tn.rnu.eniso.doserunner.custody.bo.CustodyChain;
import tn.rnu.eniso.doserunner.custody.bo.Link;
import tn.rnu.eniso.doserunner.custody.dao.CustodyEvent;
import tn.rnu.eniso.doserunner.custody.dao.CustodyEventRepository;

import java.util.Base64;
import java.util.HexFormat;
import java.util.List;

/**
 * The only entry point of custody-service for {@code pres} (REST, Thymeleaf, and later the Artemis listener).
 * Maps JPA entities to business {@link Link}s and back, so {@code bo} never sees JPA.
 */
@Service
public class CustodyFacade {

    private static final Logger log = LoggerFactory.getLogger(CustodyFacade.class);
    private static final HexFormat HEX = HexFormat.of();

    private final CustodyEventRepository repository;
    private final CustodyChain chain;

    public CustodyFacade(CustodyEventRepository repository, CustodyChain chain) {
        this.repository = repository;
        this.chain = chain;
    }

    /**
     * Appends a signed link to the order's chain. Idempotent on {@code eventId}: recording the same event twice
     * returns the existing link. If two appends to the same order race, the {@code (order_id, seq)} unique
     * constraint makes one transaction fail; the caller (Artemis listener) gets the exception and the message is
     * redelivered, which then appends after the winner.
     */
    @Transactional
    public CustodyLinkView record(RecordCommand cmd) {
        var existing = repository.findByEventId(cmd.eventId());
        if (existing.isPresent()) {
            log.debug("custody event {} already recorded for order {}, ignoring duplicate", cmd.eventId(), cmd.orderId());
            return toView(existing.get());
        }
        Link last = repository.findFirstByOrderIdOrderBySeqDesc(cmd.orderId()).map(CustodyFacade::toLink).orElse(null);
        Link link = chain.append(last, new CustodyChain.Draft(cmd.eventId(), cmd.orderId(), cmd.type(),
                cmd.actorId(), cmd.robotId(), cmd.payload(), cmd.occurredAt()));
        CustodyEvent saved = repository.save(toEntity(link));
        log.info("custody link recorded order={} seq={} type={}", link.orderId(), link.seq(), link.type());
        return toView(saved);
    }

    @Transactional(readOnly = true)
    public List<CustodyLinkView> chain(String orderId) {
        return repository.findByOrderIdOrderBySeqAsc(orderId).stream().map(CustodyFacade::toView).toList();
    }

    /** Re-reads the whole chain, re-checks every hash and signature, and reports the first broken link. */
    @Transactional(readOnly = true)
    public VerificationReport verify(String orderId) {
        List<Link> links = repository.findByOrderIdOrderBySeqAsc(orderId).stream().map(CustodyFacade::toLink).toList();
        CustodyChain.Verification v = chain.verify(orderId, links);
        if (!v.valid() && v.links() > 0) {
            log.warn("custody chain INVALID order={} brokenAtSeq={} reason={}", orderId, v.brokenAtSeq(), v.reason());
        }
        return new VerificationReport(orderId, v.valid(), v.links(), v.brokenAtSeq(), v.reason());
    }

    /** Ed25519 public key, base64, for offline verification. */
    public String publicKeyBase64() {
        return Base64.getEncoder().encodeToString(chain.publicKey());
    }

    public String keyFingerprint() {
        return chain.keyFingerprint();
    }

    private static Link toLink(CustodyEvent e) {
        return new Link(e.getEventId(), e.getOrderId(), e.getSeq(), e.getType(), e.getActorId(), e.getRobotId(),
                e.getPayloadHash(), e.getPrevHash(), e.getSignature(), e.getTs());
    }

    private static CustodyEvent toEntity(Link l) {
        return new CustodyEvent(l.eventId(), l.orderId(), l.seq(), l.type(), l.actorId(), l.robotId(),
                l.payloadHash(), l.prevHash(), l.signature(), l.ts());
    }

    private static CustodyLinkView toView(CustodyEvent e) {
        return new CustodyLinkView(e.getSeq(), e.getType().name(), e.getEventId(), e.getActorId(), e.getRobotId(),
                e.getTs(), HEX.formatHex(e.getPayloadHash()), HEX.formatHex(e.getPrevHash()),
                HEX.formatHex(e.getSignature()));
    }
}
