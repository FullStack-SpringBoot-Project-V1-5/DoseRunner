package tn.rnu.eniso.doserunner.identity.pres;

import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tn.rnu.eniso.doserunner.identity.facade.AuthFacade;
import tn.rnu.eniso.doserunner.identity.facade.dto.MeView;
import tn.rnu.eniso.doserunner.identity.facade.dto.TokenResponse;
import tn.rnu.eniso.doserunner.identity.pres.dto.LoginRequest;

import java.util.UUID;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthFacade auth;

    public AuthController(AuthFacade auth) {
        this.auth = auth;
    }

    /** Public (listed in {@code doserunner.security.public-paths}). */
    @PostMapping("/login")
    public TokenResponse login(@Valid @RequestBody LoginRequest req) {
        return auth.login(req.username(), req.password());
    }

    /** Any authenticated user. {@code sub} is the user id. */
    @GetMapping("/me")
    public MeView me(@AuthenticationPrincipal Jwt jwt) {
        return auth.me(UUID.fromString(jwt.getSubject()));
    }
}
