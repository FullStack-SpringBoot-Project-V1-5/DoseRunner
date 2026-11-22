package tn.rnu.eniso.doserunner.identity.pres.dto;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record RolesRequest(@NotEmpty List<String> roles) {
}
