package tn.rnu.eniso.doserunner.custody.pres;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import tn.rnu.eniso.doserunner.custody.facade.CustodyFacade;
import tn.rnu.eniso.doserunner.custody.facade.CustodyLinkView;
import tn.rnu.eniso.doserunner.custody.facade.VerificationReport;

import java.util.List;
import java.util.Map;

/**
 * Read-only ledger API (README, custody-service). There is deliberately no write endpoint: links are created only
 * from Artemis events (ADR 0001 #12). Roles (AUDITOR, PHARMACIST) are enforced once commons/sec exists.
 */
@RestController
@RequestMapping("/custody")
public class CustodyController {

    private final CustodyFacade facade;

    public CustodyController(CustodyFacade facade) {
        this.facade = facade;
    }

    @GetMapping("/orders/{orderId}")
    public List<CustodyLinkView> chain(@PathVariable String orderId) {
        List<CustodyLinkView> chain = facade.chain(orderId);
        if (chain.isEmpty()) {
            throw noRecord(orderId);
        }
        return chain;
    }

    @GetMapping("/orders/{orderId}/verify")
    public VerificationReport verify(@PathVariable String orderId) {
        VerificationReport report = facade.verify(orderId);
        if (report.links() == 0) {
            throw noRecord(orderId);
        }
        return report;
    }

    @GetMapping("/public-key")
    public Map<String, String> publicKey() {
        return Map.of("algorithm", "Ed25519", "publicKey", facade.publicKeyBase64());
    }

    private static ResponseStatusException noRecord(String orderId) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "no custody record for order " + orderId);
    }
}
