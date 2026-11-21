package tn.rnu.eniso.doserunner.dispatch.dao;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MissionRepository extends JpaRepository<Mission, UUID> {
    List<Mission> findByOrderId(UUID orderId);
}
