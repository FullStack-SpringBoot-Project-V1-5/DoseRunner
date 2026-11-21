package tn.rnu.eniso.doserunner.dispatch.dao;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface MissionRepository extends JpaRepository<Mission, UUID> {
}
