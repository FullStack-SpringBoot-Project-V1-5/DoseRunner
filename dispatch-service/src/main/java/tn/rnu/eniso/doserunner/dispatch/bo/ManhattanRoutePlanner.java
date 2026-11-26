package tn.rnu.eniso.doserunner.dispatch.bo;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import tn.rnu.eniso.doserunner.contracts.common.GridPoint;

public final class ManhattanRoutePlanner implements RoutePlanner {
    // Bound materialized Avro arrays from untrusted coordinates, not the hospital map.
    public static final int MAX_MOVEMENTS = 10000;

    @Override
    public Route plan(GridPoint pharmacy, GridPoint destination) {
        Objects.requireNonNull(pharmacy, "pharmacy");
        Objects.requireNonNull(destination, "destination");
        int x = pharmacy.getX(), y = pharmacy.getY();
        if (x < 0 || y < 0 || destination.getX() < 0 || destination.getY() < 0) {
            throw new IllegalArgumentException("Grid coordinates must be nonnegative");
        }
        long distance = Math.abs((long) x - destination.getX()) + Math.abs((long) y - destination.getY());
        if (distance > MAX_MOVEMENTS) throw new IllegalArgumentException("Route exceeds 10000 movements");
        var path = new ArrayList<GridPoint>((int) distance + 1);
        path.add(new GridPoint(x, y));
        while (x != destination.getX()) {
            x += Integer.compare(destination.getX(), x);
            path.add(new GridPoint(x, y));
        }
        while (y != destination.getY()) {
            y += Integer.compare(destination.getY(), y);
            path.add(new GridPoint(x, y));
        }
        return new Route(List.copyOf(path), (int) distance);
    }
}
