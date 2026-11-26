package tn.rnu.eniso.doserunner.dispatch.bo;

import java.util.List;
import tn.rnu.eniso.doserunner.contracts.common.GridPoint;

public interface RoutePlanner {
    record Route(List<GridPoint> path, int etaSeconds) { }
    Route plan(GridPoint pharmacy, GridPoint destination);
}
