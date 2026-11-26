package tn.rnu.eniso.doserunner.identity.facade.dto;

/** Body of {@code POST /auth/login}. */
public record TokenResponse(String accessToken, String tokenType, long expiresIn) {
}
