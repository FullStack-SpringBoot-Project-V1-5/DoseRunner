package tn.rnu.eniso.doserunner.dispatch.bo;

import java.util.Collection;
import java.util.Optional;

/** Pure selection SPI, discovered using {@link java.util.ServiceLoader}. */
public interface DispatchStrategy {
    /**
     * Selects a candidate without mutating fleet state; empty means no eligible robot.
     * Pickup coordinates must be nonnegative; candidates and their entries must be nonnull.
     */
    Optional<RobotCandidate> select(Collection<RobotCandidate> candidates, int pickupX, int pickupY);
}
