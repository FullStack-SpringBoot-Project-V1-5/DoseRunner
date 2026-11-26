package tn.rnu.eniso.doserunner.order.pres;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import tn.rnu.eniso.doserunner.order.facade.PlaceOrderCommand;

public record PlaceOrderRequest(
        @NotBlank @Size(max = 64) String patientRef,
        @NotBlank @Size(max = 32) String drugCode,
        @NotNull @Positive @Max(100) Integer qty,
        @NotBlank @Size(max = 32) String ward,
        @Size(max = 16) String room) {

    PlaceOrderCommand toCommand() {
        return new PlaceOrderCommand(patientRef, drugCode, qty, ward, room);
    }
}
