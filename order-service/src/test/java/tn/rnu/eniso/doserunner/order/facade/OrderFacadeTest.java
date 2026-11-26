package tn.rnu.eniso.doserunner.order.facade;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tn.rnu.eniso.doserunner.order.bo.OrderBO;
import tn.rnu.eniso.doserunner.order.bo.StockBO;
import tn.rnu.eniso.doserunner.order.dao.DrugEntity;
import tn.rnu.eniso.doserunner.order.dao.DrugRepository;
import tn.rnu.eniso.doserunner.order.dao.MedicationOrderEntity;
import tn.rnu.eniso.doserunner.order.dao.MedicationOrderRepository;
import tn.rnu.eniso.doserunner.order.dao.StockItemEntity;
import tn.rnu.eniso.doserunner.order.dao.StockItemRepository;
import tn.rnu.eniso.doserunner.order.dao.WardEntity;
import tn.rnu.eniso.doserunner.order.dao.WardRepository;
import tn.rnu.eniso.doserunner.order.messaging.OrderEventPublisher;
import tn.rnu.eniso.doserunner.order.messaging.OrderPlacedNotice;
import tn.rnu.eniso.doserunner.order.messaging.OrderValidatedNotice;
import tn.rnu.eniso.doserunner.order.shared.OrderStatus;
import tn.rnu.eniso.doserunner.order.shared.error.IllegalOrderTransitionException;
import tn.rnu.eniso.doserunner.order.shared.error.InsufficientStockException;
import tn.rnu.eniso.doserunner.order.shared.error.InvalidOrderException;
import tn.rnu.eniso.doserunner.order.shared.error.NotFoundException;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderFacadeTest {

    private static final Instant NOW = Instant.parse("2026-11-20T10:00:00Z");

    @Mock
    private MedicationOrderRepository orders;
    @Mock
    private DrugRepository drugs;
    @Mock
    private StockItemRepository stockItems;
    @Mock
    private WardRepository wards;
    @Mock
    private OrderEventPublisher events;

    private OrderFacade facade;
    private DrugEntity morphine;
    private WardEntity cardio;

    @BeforeEach
    void setUp() {
        facade = new OrderFacade(orders, drugs, stockItems, wards, new OrderBO(), new StockBO(), events,
                Clock.fixed(NOW, ZoneOffset.UTC));
        morphine = new DrugEntity("MORPH-10", "Morphine", "INJECTION", true, NOW);
        cardio = new WardEntity("CARDIO-2", "Cardiology", 12, 3, true);
    }

    @Test
    void placeReservesStockSavesTheOrderAndAnnouncesIt() {
        StockItemEntity stock = new StockItemEntity("MORPH-10", 10, 0);
        when(drugs.findById("MORPH-10")).thenReturn(Optional.of(morphine));
        when(wards.findById("CARDIO-2")).thenReturn(Optional.of(cardio));
        when(stockItems.findForUpdate("MORPH-10")).thenReturn(Optional.of(stock));
        when(orders.save(any(MedicationOrderEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        OrderView view = facade.place(new PlaceOrderCommand("P-0042", "MORPH-10", 2, "CARDIO-2", "214"), "u-nurse");

        assertThat(view.status()).isEqualTo(OrderStatus.PLACED);
        assertThat(view.controlled()).isTrue();
        assertThat(view.nurseId()).isEqualTo("u-nurse");
        assertThat(stock.getAvailable()).isEqualTo(8);
        assertThat(stock.getReserved()).isEqualTo(2);

        ArgumentCaptor<OrderPlacedNotice> notice = ArgumentCaptor.forClass(OrderPlacedNotice.class);
        verify(events).publish(notice.capture());
        assertThat(notice.getValue().orderId()).isEqualTo(view.id());
        assertThat(notice.getValue().destination().gridX()).isEqualTo(12);
        assertThat(notice.getValue().destination().room()).isEqualTo("214");
        assertThat(notice.getValue().occurredAt()).isEqualTo(NOW);
    }

    @Test
    void placeWithoutEnoughStockSavesNothingAndPublishesNothing() {
        when(drugs.findById("MORPH-10")).thenReturn(Optional.of(morphine));
        when(wards.findById("CARDIO-2")).thenReturn(Optional.of(cardio));
        when(stockItems.findForUpdate("MORPH-10")).thenReturn(Optional.of(new StockItemEntity("MORPH-10", 1, 0)));

        assertThatThrownBy(() -> facade.place(new PlaceOrderCommand("P-1", "MORPH-10", 2, "CARDIO-2", null), "u"))
                .isInstanceOf(InsufficientStockException.class);

        verify(orders, never()).save(any());
        verifyNoInteractions(events);
    }

    @Test
    void placeWithAnUnknownDrugIsInvalid() {
        when(drugs.findById("NOPE")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> facade.place(new PlaceOrderCommand("P-1", "NOPE", 1, "CARDIO-2", null), "u"))
                .isInstanceOf(InvalidOrderException.class)
                .hasMessageContaining("NOPE");
        verifyNoInteractions(stockItems, events);
    }

    @Test
    void placeWithAnUnknownWardIsInvalid() {
        when(drugs.findById("MORPH-10")).thenReturn(Optional.of(morphine));
        when(wards.findById("MARS")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> facade.place(new PlaceOrderCommand("P-1", "MORPH-10", 1, "MARS", null), "u"))
                .isInstanceOf(InvalidOrderException.class);
        verifyNoInteractions(stockItems, events);
    }

    @Test
    void validateAnnouncesOrderValidatedWithTheDestination() {
        MedicationOrderEntity order = placedOrder();
        when(orders.findById(order.getId())).thenReturn(Optional.of(order));
        when(wards.findById("CARDIO-2")).thenReturn(Optional.of(cardio));

        OrderView view = facade.validate(order.getId(), "u-pharma");

        assertThat(view.status()).isEqualTo(OrderStatus.VALIDATED);
        ArgumentCaptor<OrderValidatedNotice> notice = ArgumentCaptor.forClass(OrderValidatedNotice.class);
        verify(events).publish(notice.capture());
        assertThat(notice.getValue().pharmacistId()).isEqualTo("u-pharma");
        assertThat(notice.getValue().destination().ward()).isEqualTo("CARDIO-2");
        assertThat(notice.getValue().controlled()).isTrue();
    }

    @Test
    void validatingTwiceIsAConflictAndPublishesOnce() {
        MedicationOrderEntity order = placedOrder();
        when(orders.findById(order.getId())).thenReturn(Optional.of(order));
        when(wards.findById("CARDIO-2")).thenReturn(Optional.of(cardio));
        facade.validate(order.getId(), "u-pharma");

        assertThatThrownBy(() -> facade.validate(order.getId(), "u-pharma"))
                .isInstanceOf(IllegalOrderTransitionException.class);
        verify(events).publish(any(OrderValidatedNotice.class));
    }

    @Test
    void rejectReleasesTheReservedStock() {
        MedicationOrderEntity order = placedOrder();
        StockItemEntity stock = new StockItemEntity("MORPH-10", 8, 2);
        when(orders.findById(order.getId())).thenReturn(Optional.of(order));
        when(stockItems.findForUpdate("MORPH-10")).thenReturn(Optional.of(stock));

        OrderView view = facade.reject(order.getId(), "u-pharma", "duplicate");

        assertThat(view.status()).isEqualTo(OrderStatus.REJECTED);
        assertThat(view.rejectReason()).isEqualTo("duplicate");
        assertThat(stock.getAvailable()).isEqualTo(10);
        assertThat(stock.getReserved()).isZero();
        verifyNoInteractions(events);
    }

    @Test
    void unknownOrderIsNotFound() {
        UUID id = UUID.randomUUID();
        when(orders.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> facade.get(id)).isInstanceOf(NotFoundException.class);
    }

    private MedicationOrderEntity placedOrder() {
        return new MedicationOrderEntity(UUID.randomUUID(), "P-0042", "MORPH-10", 2, true, "CARDIO-2", "214",
                "u-nurse", NOW);
    }
}
