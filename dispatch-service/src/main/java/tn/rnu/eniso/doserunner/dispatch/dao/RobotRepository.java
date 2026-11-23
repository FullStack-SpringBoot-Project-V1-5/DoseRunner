package tn.rnu.eniso.doserunner.dispatch.dao;

import jakarta.persistence.LockModeType;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface RobotRepository extends JpaRepository<Robot, String> {
    @Modifying
    @Query("update Robot r set r.x = :x, r.y = :y, r.battery = :battery where r.id = :id")
    int updateTelemetry(String id, int x, int y, int battery);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Robot r order by r.id")
    List<Robot> lockFleet();
}
