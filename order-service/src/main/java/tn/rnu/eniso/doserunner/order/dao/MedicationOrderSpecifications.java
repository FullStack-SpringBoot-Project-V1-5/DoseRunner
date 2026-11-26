package tn.rnu.eniso.doserunner.order.dao;

import org.springframework.data.jpa.domain.Specification;
import tn.rnu.eniso.doserunner.order.shared.OrderStatus;

public final class MedicationOrderSpecifications {

    public static Specification<MedicationOrderEntity> matching(OrderStatus status, String wardCode) {
        Specification<MedicationOrderEntity> spec = Specification.unrestricted();
        if (status != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("status"), status));
        }
        if (wardCode != null && !wardCode.isBlank()) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("wardCode"), wardCode));
        }
        return spec;
    }

    private MedicationOrderSpecifications() {
    }
}
