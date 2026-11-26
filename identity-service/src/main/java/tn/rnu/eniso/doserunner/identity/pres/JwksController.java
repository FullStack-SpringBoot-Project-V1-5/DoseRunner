package tn.rnu.eniso.doserunner.identity.pres;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import tn.rnu.eniso.doserunner.identity.config.PublicJwks;

import java.util.Map;

/** The public half of the signing key. The other services (and Istio's RequestAuthentication, week 3) read it. */
@RestController
public class JwksController {

    private final PublicJwks jwks;

    public JwksController(PublicJwks jwks) {
        this.jwks = jwks;
    }

    @GetMapping("/.well-known/jwks.json")
    public Map<String, Object> jwks() {
        return jwks.json();
    }
}
