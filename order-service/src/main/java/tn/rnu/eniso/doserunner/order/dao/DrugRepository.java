package tn.rnu.eniso.doserunner.order.dao;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DrugRepository extends JpaRepository<DrugEntity, String> {

    List<DrugEntity> findAllByOrderByNameAsc();
}
