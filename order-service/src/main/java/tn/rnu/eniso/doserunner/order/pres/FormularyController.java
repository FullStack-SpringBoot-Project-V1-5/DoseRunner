package tn.rnu.eniso.doserunner.order.pres;

import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tn.rnu.eniso.doserunner.order.facade.DrugView;
import tn.rnu.eniso.doserunner.order.facade.FormularyFacade;

import java.util.List;

@RestController
@RequestMapping("/formulary")
public class FormularyController {

    private final FormularyFacade formularyFacade;

    public FormularyController(FormularyFacade formularyFacade) {
        this.formularyFacade = formularyFacade;
    }

    @GetMapping
    public List<DrugView> list() {
        return formularyFacade.list();
    }

    @GetMapping("/{code}")
    public DrugView get(@PathVariable String code) {
        return formularyFacade.get(code);
    }

    @PutMapping("/{code}")
    @PreAuthorize("hasRole('PHARMACIST')")
    public DrugView update(@PathVariable String code, @Valid @RequestBody UpdateDrugRequest body) {
        return formularyFacade.update(code, body.toCommand());
    }
}
