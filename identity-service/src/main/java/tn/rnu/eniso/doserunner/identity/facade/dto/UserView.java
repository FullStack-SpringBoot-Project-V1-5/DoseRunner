package tn.rnu.eniso.doserunner.identity.facade.dto;

import java.util.List;
import java.util.UUID;

/** A user as the admin API shows it. Never carries the password hash. */
public record UserView(UUID id, String username, List<String> roles, boolean enabled) {
}
