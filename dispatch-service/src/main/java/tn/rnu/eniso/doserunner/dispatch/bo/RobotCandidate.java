package tn.rnu.eniso.doserunner.dispatch.bo;

import java.util.Objects;
import tn.rnu.eniso.doserunner.dispatch.dao.RobotStatus;

/** Immutable fleet snapshot; selection never changes or retains a managed JPA entity. */
public record RobotCandidate(String id, int x, int y, int battery, RobotStatus status) {
    public RobotCandidate {
        if (id == null || id.isBlank() || id.length() > 64) {
            throw new IllegalArgumentException("Robot id must contain 1..64 nonblank characters");
        }
        if (x < 0 || y < 0) {
            throw new IllegalArgumentException("Grid coordinates must be nonnegative");
        }
        if (battery < 0 || battery > 100) {
            throw new IllegalArgumentException("Battery must be in 0..100 percent");
        }
        Objects.requireNonNull(status, "status");
    }
}
