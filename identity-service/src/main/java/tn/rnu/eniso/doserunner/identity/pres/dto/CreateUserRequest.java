package tn.rnu.eniso.doserunner.identity.pres.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/** Password strength is checked by {@code CredentialPolicy}, which lists every broken rule at once. */
public record CreateUserRequest(@NotBlank String username, @NotBlank String password, @NotEmpty List<String> roles) {

    @Override
    public String toString() {
        return "CreateUserRequest[username=" + username + ", roles=" + roles + "]";
    }
}
