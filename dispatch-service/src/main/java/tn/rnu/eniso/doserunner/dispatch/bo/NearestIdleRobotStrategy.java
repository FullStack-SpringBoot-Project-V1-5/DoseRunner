package tn.rnu.eniso.doserunner.dispatch.bo;

import java.util.Collection;
import java.util.Comparator;
import java.util.Objects;
import java.util.Optional;
import tn.rnu.eniso.doserunner.dispatch.dao.RobotStatus;

/** V1: Manhattan distance to pickup, IDLE with battery >=20%, ties by robot ID. */
public final class NearestIdleRobotStrategy implements DispatchStrategy {
    @Override
    public Optional<RobotCandidate> select(Collection<RobotCandidate> candidates, int pickupX, int pickupY) {
        Objects.requireNonNull(candidates, "candidates");
        if (pickupX < 0 || pickupY < 0) {
            throw new IllegalArgumentException("Pickup coordinates must be nonnegative");
        }
        return candidates.stream()
                .map(candidate -> Objects.requireNonNull(candidate, "candidate"))
                .filter(candidate -> candidate.status() == RobotStatus.IDLE && candidate.battery() >= 20)
                .min(Comparator.comparingLong((RobotCandidate candidate) ->
                        Math.abs((long) candidate.x() - pickupX) + Math.abs((long) candidate.y() - pickupY))
                        .thenComparing(RobotCandidate::id));
    }
}
