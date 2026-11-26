package tn.rnu.eniso.doserunner.identity.facade.dto;

import java.util.List;
import java.util.UUID;

/** Body of {@code GET /auth/me}. */
public record MeView(UUID id, String username, List<String> roles) {
}
