package tn.rnu.eniso.doserunner.custody.pres;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.util.UriUtils;
import tn.rnu.eniso.doserunner.custody.facade.CustodyFacade;

import java.nio.charset.StandardCharsets;

/** Thymeleaf audit page: the timeline of one order's custody chain with a VALID / INVALID badge. */
@Controller
public class AuditPageController {

    private final CustodyFacade facade;

    public AuditPageController(CustodyFacade facade) {
        this.facade = facade;
    }

    /** Search form; {@code ?orderId=} redirects to the order's page. */
    @GetMapping("/ui/audit")
    public String search(@RequestParam(required = false) String orderId, Model model) {
        if (orderId != null && !orderId.isBlank()) {
            return "redirect:/ui/audit/" + UriUtils.encodePathSegment(orderId.strip(), StandardCharsets.UTF_8);
        }
        model.addAttribute("keyFingerprint", facade.keyFingerprint());
        return "audit";
    }

    @GetMapping("/ui/audit/{orderId}")
    public String audit(@PathVariable String orderId, Model model) {
        model.addAttribute("orderId", orderId);
        model.addAttribute("links", facade.chain(orderId));
        model.addAttribute("report", facade.verify(orderId));
        model.addAttribute("keyFingerprint", facade.keyFingerprint());
        return "audit";
    }
}
