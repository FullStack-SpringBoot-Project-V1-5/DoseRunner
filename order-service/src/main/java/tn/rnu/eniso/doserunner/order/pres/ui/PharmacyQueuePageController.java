package tn.rnu.eniso.doserunner.order.pres.ui;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import tn.rnu.eniso.doserunner.order.facade.DrugView;
import tn.rnu.eniso.doserunner.order.facade.FormularyFacade;
import tn.rnu.eniso.doserunner.order.facade.OrderFacade;
import tn.rnu.eniso.doserunner.order.pres.CurrentActor;
import tn.rnu.eniso.doserunner.order.shared.error.OrderServiceException;

import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/ui/pharmacy")
public class PharmacyQueuePageController {

    private static final String REDIRECT_QUEUE = "redirect:/ui/pharmacy/queue";

    private final OrderFacade orderFacade;
    private final FormularyFacade formularyFacade;
    private final CurrentActor currentActor;

    public PharmacyQueuePageController(OrderFacade orderFacade, FormularyFacade formularyFacade,
                                       CurrentActor currentActor) {
        this.orderFacade = orderFacade;
        this.formularyFacade = formularyFacade;
        this.currentActor = currentActor;
    }

    @GetMapping("/queue")
    public String queue(Model model) {
        Map<String, DrugView> drugs = formularyFacade.list().stream()
                .collect(Collectors.toMap(DrugView::code, Function.identity()));
        model.addAttribute("orders", orderFacade.pharmacyQueue());
        model.addAttribute("drugs", drugs);
        return "ui/pharmacy-queue";
    }

    @PostMapping("/orders/{id}/validate")
    public String validate(@PathVariable UUID id, HttpServletRequest request, RedirectAttributes redirect) {
        try {
            orderFacade.validate(id, currentActor.pharmacistId(request));
            redirect.addFlashAttribute("message", "Order " + id + " validated, dispatch notified.");
        } catch (OrderServiceException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }
        return REDIRECT_QUEUE;
    }

    @PostMapping("/orders/{id}/reject")
    public String reject(@PathVariable UUID id, @RequestParam(defaultValue = "") String reason,
                         HttpServletRequest request, RedirectAttributes redirect) {
        try {
            orderFacade.reject(id, currentActor.pharmacistId(request), reason);
            redirect.addFlashAttribute("message", "Order " + id + " rejected, stock released.");
        } catch (OrderServiceException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }
        return REDIRECT_QUEUE;
    }
}
