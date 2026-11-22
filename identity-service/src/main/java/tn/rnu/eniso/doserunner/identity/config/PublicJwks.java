package tn.rnu.eniso.doserunner.identity.config;

import java.util.Map;

/** The JWKS document with public keys only; a separate type so the private key is never injected into pres. */
public record PublicJwks(Map<String, Object> json) {
}
