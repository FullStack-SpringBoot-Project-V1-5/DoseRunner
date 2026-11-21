package tn.rnu.eniso.doserunner.dispatch.dao;

import jakarta.persistence.LockModeType;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface RobotRepository extends JpaRepository<Robot, String> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Robot r order by r.id")
    List<Robot> lockFleet();
}
