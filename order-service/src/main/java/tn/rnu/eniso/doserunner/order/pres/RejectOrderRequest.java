package tn.rnu.eniso.doserunner.order.pres;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RejectOrderRequest(@NotBlank @Size(max = 512) String reason) {
}
