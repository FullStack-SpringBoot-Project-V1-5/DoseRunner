package tn.rnu.eniso.doserunner.order.dao;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WardRepository extends JpaRepository<WardEntity, String> {

    List<WardEntity> findAllByDeliverableTrueOrderByCodeAsc();
}
