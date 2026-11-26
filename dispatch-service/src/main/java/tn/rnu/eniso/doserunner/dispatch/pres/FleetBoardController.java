package tn.rnu.eniso.doserunner.dispatch.pres;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import tn.rnu.eniso.doserunner.dispatch.facade.FleetBoardFacade;

@Controller
public class FleetBoardController {
    private final FleetBoardFacade fleet;

    public FleetBoardController(FleetBoardFacade fleet) {
        this.fleet = fleet;
    }

    @GetMapping("/ui/fleet")
    public String fleet(Model model, HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
        model.addAttribute("fleet", fleet.snapshot());
        return "fleet";
    }
}
