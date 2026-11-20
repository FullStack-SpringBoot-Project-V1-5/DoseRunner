package tn.rnu.eniso.doserunner.order.dao;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface StockItemRepository extends JpaRepository<StockItemEntity, String> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from StockItemEntity s where s.drugCode = :drugCode")
    Optional<StockItemEntity> findForUpdate(@Param("drugCode") String drugCode);

    List<StockItemEntity> findAllByOrderByDrugCodeAsc();
}
