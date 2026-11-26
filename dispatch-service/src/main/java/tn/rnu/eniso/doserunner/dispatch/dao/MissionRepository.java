package tn.rnu.eniso.doserunner.dispatch.dao;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;

public interface MissionRepository extends JpaRepository<Mission, UUID> {
    @EntityGraph(attributePaths = "robot")
    List<Mission> findByStatusOrderByIdAsc(MissionStatus status);

    List<Mission> findByOrderId(UUID orderId);
}
