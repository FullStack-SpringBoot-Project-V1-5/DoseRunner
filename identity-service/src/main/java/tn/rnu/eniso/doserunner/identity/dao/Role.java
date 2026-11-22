package tn.rnu.eniso.doserunner.identity.dao;

import java.util.Locale;

/** The five DoseRunner roles. The JWT carries their names in the {@code roles} claim. */
public enum Role {
    NURSE, PHARMACIST, OPERATOR, AUDITOR, ADMIN;

    /** Case-insensitive parse; an unknown name is a client error (400). */
    public static Role parse(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Role must not be blank");
        }
        try {
            return valueOf(name.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unknown role: " + name);
        }
    }
}
