package tn.rnu.eniso.doserunner.identity.pres.dto;

import jakarta.validation.constraints.NotNull;

public record EnabledRequest(@NotNull Boolean enabled) {
}
