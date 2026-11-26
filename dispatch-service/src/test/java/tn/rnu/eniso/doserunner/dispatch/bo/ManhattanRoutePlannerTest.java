package tn.rnu.eniso.doserunner.dispatch.bo;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import tn.rnu.eniso.doserunner.contracts.common.GridPoint;
import static org.assertj.core.api.Assertions.*;

class ManhattanRoutePlannerTest {
    final RoutePlanner planner = new ManhattanRoutePlanner();
    @ParameterizedTest
    @CsvSource({"0,0,0,0", "0,0,3,0", "0,0,0,3", "3,4,1,1", "0,0,2,3", "5,2,7,0"})
    void walksXThenYOneCellPerSecond(int x, int y, int dx, int dy) {
        var start = new GridPoint(x, y);
        var end = new GridPoint(dx, dy);
        var route = planner.plan(start, end);
        assertThat(route.path().getFirst()).isEqualTo(start);
        assertThat(route.path().getLast()).isEqualTo(end);
        assertThat(route.etaSeconds()).isEqualTo(Math.abs(dx-x)+Math.abs(dy-y));
        assertThat(route.path()).hasSize(route.etaSeconds()+1);
        for (int i=1; i<route.path().size(); i++) {
            var previous=route.path().get(i-1); var next=route.path().get(i);
            assertThat(Math.abs(previous.getX()-next.getX())+Math.abs(previous.getY()-next.getY())).isEqualTo(1);
            if (previous.getX()!=dx) assertThat(next.getY()).isEqualTo(y);
        }
        assertThat(planner.plan(start,end)).isEqualTo(route);
    }
    @Test void rejectsNegativeNullAndUnboundedRoutes() {
        assertThatThrownBy(() -> planner.plan(new GridPoint(-1,0),new GridPoint(0,0))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> planner.plan(new GridPoint(0,0),new GridPoint(0,-1))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> planner.plan(null,new GridPoint(0,0))).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> planner.plan(new GridPoint(0,0),new GridPoint(Integer.MAX_VALUE,Integer.MAX_VALUE)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
