package tn.rnu.eniso.doserunner.identity.pres.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(@NotBlank @Size(max = 64) String username, @NotBlank @Size(max = 72) String password) {

    /** Never print the password, even in a debug log of the request. */
    @Override
    public String toString() {
        return "LoginRequest[username=" + username + "]";
    }
}
