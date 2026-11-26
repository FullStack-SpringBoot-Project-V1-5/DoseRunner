package tn.rnu.eniso.doserunner.order.pres;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import tn.rnu.eniso.doserunner.order.facade.OrderFacade;
import tn.rnu.eniso.doserunner.order.facade.OrderView;
import tn.rnu.eniso.doserunner.order.facade.PageView;
import tn.rnu.eniso.doserunner.order.shared.OrderStatus;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/orders")
public class OrderController {

    private final OrderFacade orderFacade;
    private final CurrentActor currentActor;

    public OrderController(OrderFacade orderFacade, CurrentActor currentActor) {
        this.orderFacade = orderFacade;
        this.currentActor = currentActor;
    }

    @PostMapping
    @PreAuthorize("hasRole('NURSE')")
    public ResponseEntity<OrderView> place(@Valid @RequestBody PlaceOrderRequest body, HttpServletRequest request) {
        OrderView order = orderFacade.place(body.toCommand(), currentActor.nurseId(request));
        URI location = ServletUriComponentsBuilder.fromCurrentRequestUri().path("/{id}")
                .buildAndExpand(order.id()).toUri();
        return ResponseEntity.created(location).body(order);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('NURSE', 'PHARMACIST', 'AUDITOR')")
    public OrderView get(@PathVariable UUID id) {
        return orderFacade.get(id);
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('NURSE', 'PHARMACIST')")
    public PageView<OrderView> search(@RequestParam(required = false) OrderStatus status,
                                      @RequestParam(required = false) String ward,
                                      @RequestParam(defaultValue = "0") @Min(0) int page,
                                      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return orderFacade.search(status, ward, page, size);
    }
}
