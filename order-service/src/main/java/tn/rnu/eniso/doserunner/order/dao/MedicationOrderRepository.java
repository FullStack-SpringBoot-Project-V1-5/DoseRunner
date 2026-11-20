package tn.rnu.eniso.doserunner.order.dao;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import tn.rnu.eniso.doserunner.order.shared.OrderStatus;

import java.util.List;
import java.util.UUID;

public interface MedicationOrderRepository extends JpaRepository<MedicationOrderEntity, UUID>,
        JpaSpecificationExecutor<MedicationOrderEntity> {

    List<MedicationOrderEntity> findByStatusOrderByCreatedAtAsc(OrderStatus status);
}
