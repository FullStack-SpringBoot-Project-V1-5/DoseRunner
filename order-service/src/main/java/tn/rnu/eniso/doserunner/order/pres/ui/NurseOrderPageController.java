package tn.rnu.eniso.doserunner.order.pres.ui;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import tn.rnu.eniso.doserunner.order.facade.FormularyFacade;
import tn.rnu.eniso.doserunner.order.facade.OrderFacade;
import tn.rnu.eniso.doserunner.order.facade.OrderView;
import tn.rnu.eniso.doserunner.order.pres.CurrentActor;
import tn.rnu.eniso.doserunner.order.shared.error.NotFoundException;
import tn.rnu.eniso.doserunner.order.shared.error.OrderServiceException;

import java.util.UUID;

@Controller
@RequestMapping("/ui/orders")
public class NurseOrderPageController {

    private static final String FORM = "form";

    private final OrderFacade orderFacade;
    private final FormularyFacade formularyFacade;
    private final CurrentActor currentActor;

    public NurseOrderPageController(OrderFacade orderFacade, FormularyFacade formularyFacade,
                                    CurrentActor currentActor) {
        this.orderFacade = orderFacade;
        this.formularyFacade = formularyFacade;
        this.currentActor = currentActor;
    }

    @GetMapping("/new")
    public String newOrder(Model model) {
        model.addAttribute(FORM, new NewOrderForm());
        return formView(model);
    }

    @PostMapping("/new")
    public String submit(@Valid @ModelAttribute(FORM) NewOrderForm form, BindingResult binding, Model model,
                         HttpServletRequest request, RedirectAttributes redirect) {
        if (binding.hasErrors()) {
            return formView(model);
        }
        try {
            OrderView order = orderFacade.place(form.toCommand(), currentActor.nurseId(request));
            redirect.addFlashAttribute("message", "Order placed, waiting for pharmacist validation.");
            return "redirect:/ui/orders/" + order.id();
        } catch (OrderServiceException e) {
            binding.reject("order.rejected", e.getMessage());
            return formView(model);
        }
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable UUID id, Model model) {
        OrderView order = orderFacade.get(id);
        model.addAttribute("order", order);
        model.addAttribute("drug", formularyFacade.get(order.drugCode()));
        return "ui/order-detail";
    }

    @ExceptionHandler(NotFoundException.class)
    public ModelAndView notFound(NotFoundException e) {
        ModelAndView view = new ModelAndView("ui/not-found", HttpStatus.NOT_FOUND);
        view.addObject("message", e.getMessage());
        return view;
    }

    private String formView(Model model) {
        model.addAttribute("drugs", formularyFacade.list());
        model.addAttribute("wards", orderFacade.deliveryWards());
        return "ui/order-new";
    }
}
