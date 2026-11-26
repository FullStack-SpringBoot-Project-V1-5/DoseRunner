package tn.rnu.eniso.doserunner.order.pres;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tn.rnu.eniso.doserunner.order.facade.OrderFacade;
import tn.rnu.eniso.doserunner.order.facade.OrderView;
import tn.rnu.eniso.doserunner.order.facade.StockFacade;
import tn.rnu.eniso.doserunner.order.facade.StockView;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/pharmacy")
@PreAuthorize("hasRole('PHARMACIST')")
public class PharmacyController {

    private final OrderFacade orderFacade;
    private final StockFacade stockFacade;
    private final CurrentActor currentActor;

    public PharmacyController(OrderFacade orderFacade, StockFacade stockFacade, CurrentActor currentActor) {
        this.orderFacade = orderFacade;
        this.stockFacade = stockFacade;
        this.currentActor = currentActor;
    }

    @GetMapping("/queue")
    public List<OrderView> queue() {
        return orderFacade.pharmacyQueue();
    }

    @PostMapping("/orders/{id}/validate")
    public OrderView validate(@PathVariable UUID id, HttpServletRequest request) {
        return orderFacade.validate(id, currentActor.pharmacistId(request));
    }

    @PostMapping("/orders/{id}/reject")
    public OrderView reject(@PathVariable UUID id, @Valid @RequestBody RejectOrderRequest body,
                            HttpServletRequest request) {
        return orderFacade.reject(id, currentActor.pharmacistId(request), body.reason());
    }

    @GetMapping("/stock")
    public List<StockView> stock() {
        return stockFacade.list();
    }
}
