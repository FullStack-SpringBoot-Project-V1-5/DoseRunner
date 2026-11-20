package tn.rnu.eniso.doserunner.order.bo;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import tn.rnu.eniso.doserunner.order.dao.DrugEntity;
import tn.rnu.eniso.doserunner.order.dao.MedicationOrderEntity;
import tn.rnu.eniso.doserunner.order.dao.WardEntity;
import tn.rnu.eniso.doserunner.order.shared.OrderStatus;
import tn.rnu.eniso.doserunner.order.shared.error.IllegalOrderTransitionException;
import tn.rnu.eniso.doserunner.order.shared.error.InvalidOrderException;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderBOTest {

    private static final Instant T0 = Instant.parse("2026-11-20T08:00:00Z");
    private static final Instant T1 = Instant.parse("2026-11-20T08:05:00Z");
    private static final DrugEntity PARACETAMOL = new DrugEntity("PARA-500", "Paracetamol", "TABLET", false, T0);
    private static final DrugEntity MORPHINE = new DrugEntity("MORPH-10", "Morphine", "INJECTION", true, T0);
    private static final WardEntity CARDIO = new WardEntity("CARDIO-2", "Cardiology", 12, 3, true);
    private static final WardEntity PHARMACY = new WardEntity("PHARMACY", "Pharmacy", 0, 0, false);

    private final OrderBO orderBO = new OrderBO();

    @Test
    void createBuildsAPlacedOrderWithAControlledSnapshot() {
        UUID id = UUID.randomUUID();

        MedicationOrderEntity order = orderBO.create(id, " P-0042 ", MORPHINE, 2, CARDIO, " 214 ", "u-nurse", T0);

        assertThat(order.getId()).isEqualTo(id);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PLACED);
        assertThat(order.getPatientRef()).isEqualTo("P-0042");
        assertThat(order.getRoom()).isEqualTo("214");
        assertThat(order.isControlled()).isTrue();
        assertThat(order.getCreatedAt()).isEqualTo(T0);
        assertThat(order.getUpdatedAt()).isEqualTo(T0);
    }

    @Test
    void blankRoomIsStoredAsNull() {
        MedicationOrderEntity order = orderBO.create(UUID.randomUUID(), "P-1", PARACETAMOL, 1, CARDIO, "  ", "u", T0);

        assertThat(order.getRoom()).isNull();
    }

    @Test
    void controlledSubstancesAreCappedPerOrder() {
        assertThatThrownBy(() -> orderBO.create(UUID.randomUUID(), "P-1", MORPHINE, OrderBO.MAX_CONTROLLED_QTY + 1,
                CARDIO, null, "u", T0))
                .isInstanceOf(InvalidOrderException.class)
                .hasMessageContaining("Controlled substance");
        assertThat(orderBO.create(UUID.randomUUID(), "P-1", PARACETAMOL, OrderBO.MAX_CONTROLLED_QTY + 1, CARDIO,
                null, "u", T0).getQty()).isEqualTo(OrderBO.MAX_CONTROLLED_QTY + 1);
    }

    @Test
    void createRejectsInvalidInput() {
        assertThatThrownBy(() -> orderBO.create(UUID.randomUUID(), " ", PARACETAMOL, 1, CARDIO, null, "u", T0))
                .isInstanceOf(InvalidOrderException.class);
        assertThatThrownBy(() -> orderBO.create(UUID.randomUUID(), "P-1", PARACETAMOL, 0, CARDIO, null, "u", T0))
                .isInstanceOf(InvalidOrderException.class);
        assertThatThrownBy(() -> orderBO.create(UUID.randomUUID(), "P-1", PARACETAMOL, OrderBO.MAX_QTY + 1, CARDIO,
                null, "u", T0)).isInstanceOf(InvalidOrderException.class);
        assertThatThrownBy(() -> orderBO.create(UUID.randomUUID(), "P-1", PARACETAMOL, 1, CARDIO, null, null, T0))
                .isInstanceOf(InvalidOrderException.class);
    }

    @Test
    void thePharmacyIsNotADeliveryDestination() {
        assertThatThrownBy(() -> orderBO.create(UUID.randomUUID(), "P-1", PARACETAMOL, 1, PHARMACY, null, "u", T0))
                .isInstanceOf(InvalidOrderException.class)
                .hasMessageContaining("PHARMACY");
    }

    @Test
    void validateMovesPlacedToValidatedAndRecordsThePharmacist() {
        MedicationOrderEntity order = placed();

        orderBO.validate(order, "u-pharma", T1);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.VALIDATED);
        assertThat(order.getPharmacistId()).isEqualTo("u-pharma");
        assertThat(order.getUpdatedAt()).isEqualTo(T1);
    }

    @Test
    void rejectRequiresAReasonAndRecordsIt() {
        MedicationOrderEntity order = placed();

        assertThatThrownBy(() -> orderBO.reject(order, "u-pharma", " ", T1)).isInstanceOf(InvalidOrderException.class);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PLACED);

        orderBO.reject(order, "u-pharma", " wrong dose ", T1);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.REJECTED);
        assertThat(order.getRejectReason()).isEqualTo("wrong dose");
    }

    @Test
    void anOrderCannotBeValidatedTwice() {
        MedicationOrderEntity order = placed();
        orderBO.validate(order, "u-pharma", T1);

        assertThatThrownBy(() -> orderBO.validate(order, "u-pharma", T1))
                .isInstanceOf(IllegalOrderTransitionException.class);
        assertThatThrownBy(() -> orderBO.reject(order, "u-pharma", "late", T1))
                .isInstanceOf(IllegalOrderTransitionException.class);
    }

    @Test
    void validateRequiresAPharmacist() {
        assertThatThrownBy(() -> orderBO.validate(placed(), "", T1)).isInstanceOf(InvalidOrderException.class);
    }

    @ParameterizedTest
    @EnumSource(value = OrderStatus.class, names = {"COMPLETED", "REJECTED"})
    void terminalStatusesAllowNoTransition(OrderStatus status) {
        assertThat(status.isTerminal()).isTrue();
        assertThat(status.allowedNext()).isEmpty();
    }

    @Test
    void sagaPathIsAllowedInOrder() {
        assertThat(OrderStatus.PLACED.canMoveTo(OrderStatus.VALIDATED)).isTrue();
        assertThat(OrderStatus.VALIDATED.canMoveTo(OrderStatus.ASSIGNED)).isTrue();
        assertThat(OrderStatus.ASSIGNED.canMoveTo(OrderStatus.PICKED_UP)).isTrue();
        assertThat(OrderStatus.PICKED_UP.canMoveTo(OrderStatus.COMPLETED)).isTrue();
        assertThat(OrderStatus.PLACED.canMoveTo(OrderStatus.COMPLETED)).isFalse();
        assertThat(OrderStatus.VALIDATED.canMoveTo(OrderStatus.REJECTED)).isFalse();
    }

    private MedicationOrderEntity placed() {
        return orderBO.create(UUID.randomUUID(), "P-0042", PARACETAMOL, 2, CARDIO, "214", "u-nurse", T0);
    }
}
