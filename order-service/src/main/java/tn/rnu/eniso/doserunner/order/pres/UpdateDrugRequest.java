package tn.rnu.eniso.doserunner.order.pres;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import tn.rnu.eniso.doserunner.order.facade.UpdateDrugCommand;

public record UpdateDrugRequest(
        @NotBlank @Size(max = 128) String name,
        @NotBlank @Size(max = 32) String form,
        @NotNull Boolean controlled) {

    UpdateDrugCommand toCommand() {
        return new UpdateDrugCommand(name, form, controlled);
    }
}
