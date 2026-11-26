package tn.rnu.eniso.doserunner.order.bo;

import org.springframework.stereotype.Component;
import tn.rnu.eniso.doserunner.order.dao.DrugEntity;
import tn.rnu.eniso.doserunner.order.shared.error.InvalidOrderException;

import java.time.Instant;
import java.util.Locale;

@Component
public class FormularyBO {

    public void update(DrugEntity drug, String name, String form, boolean controlled, Instant now) {
        if (name == null || name.isBlank()) {
            throw new InvalidOrderException("Drug name is required");
        }
        if (form == null || form.isBlank()) {
            throw new InvalidOrderException("Drug form is required");
        }
        drug.setName(name.strip());
        drug.setForm(form.strip().toUpperCase(Locale.ROOT));
        drug.setControlled(controlled);
        drug.setUpdatedAt(now);
    }
}
