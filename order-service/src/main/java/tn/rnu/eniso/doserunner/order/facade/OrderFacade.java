package tn.rnu.eniso.doserunner.order.facade;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tn.rnu.eniso.doserunner.order.bo.OrderBO;
import tn.rnu.eniso.doserunner.order.bo.StockBO;
import tn.rnu.eniso.doserunner.order.dao.DrugEntity;
import tn.rnu.eniso.doserunner.order.dao.DrugRepository;
import tn.rnu.eniso.doserunner.order.dao.MedicationOrderEntity;
import tn.rnu.eniso.doserunner.order.dao.MedicationOrderRepository;
import tn.rnu.eniso.doserunner.order.dao.MedicationOrderSpecifications;
import tn.rnu.eniso.doserunner.order.dao.StockItemEntity;
import tn.rnu.eniso.doserunner.order.dao.StockItemRepository;
import tn.rnu.eniso.doserunner.order.dao.WardEntity;
import tn.rnu.eniso.doserunner.order.dao.WardRepository;
import tn.rnu.eniso.doserunner.order.messaging.DeliveryDestination;
import tn.rnu.eniso.doserunner.order.messaging.OrderEventPublisher;
import tn.rnu.eniso.doserunner.order.messaging.OrderPlacedNotice;
import tn.rnu.eniso.doserunner.order.messaging.OrderValidatedNotice;
import tn.rnu.eniso.doserunner.order.shared.OrderStatus;
import tn.rnu.eniso.doserunner.order.shared.error.InsufficientStockException;
import tn.rnu.eniso.doserunner.order.shared.error.InvalidOrderException;
import tn.rnu.eniso.doserunner.order.shared.error.NotFoundException;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class OrderFacade {

    private static final Logger log = LoggerFactory.getLogger(OrderFacade.class);

    private final MedicationOrderRepository orders;
    private final DrugRepository drugs;
    private final StockItemRepository stockItems;
    private final WardRepository wards;
    private final OrderBO orderBO;
    private final StockBO stockBO;
    private final OrderEventPublisher events;
    private final Clock clock;

    public OrderFacade(MedicationOrderRepository orders, DrugRepository drugs, StockItemRepository stockItems,
                       WardRepository wards, OrderBO orderBO, StockBO stockBO, OrderEventPublisher events,
                       Clock clock) {
        this.orders = orders;
        this.drugs = drugs;
        this.stockItems = stockItems;
        this.wards = wards;
        this.orderBO = orderBO;
        this.stockBO = stockBO;
        this.events = events;
        this.clock = clock;
    }

    @Transactional
    public OrderView place(PlaceOrderCommand command, String nurseId) {
        DrugEntity drug = drugs.findById(command.drugCode())
                .orElseThrow(() -> new InvalidOrderException("Unknown drug code " + command.drugCode()));
        WardEntity ward = wards.findById(command.ward())
                .orElseThrow(() -> new InvalidOrderException("Unknown ward " + command.ward()));
        Instant now = clock.instant();
        MedicationOrderEntity order = orderBO.create(UUID.randomUUID(), command.patientRef(), drug, command.qty(),
                ward, command.room(), nurseId, now);
        StockItemEntity stock = stockItems.findForUpdate(drug.getCode())
                .orElseThrow(() -> new InsufficientStockException(drug.getCode(), command.qty(), 0));
        stockBO.reserve(stock, order.getQty());
        MedicationOrderEntity saved = orders.save(order);
        events.publish(new OrderPlacedNotice(saved.getId(), saved.getPatientRef(), saved.getDrugCode(),
                saved.getQty(), destinationOf(saved, ward), saved.isControlled(), saved.getNurseId(), now));
        log.info("Order {} placed by {}: {} x {} for ward {}, stock left {}", saved.getId(), nurseId, saved.getQty(),
                saved.getDrugCode(), saved.getWardCode(), stock.getAvailable());
        return OrderView.from(saved);
    }

    @Transactional
    public OrderView validate(UUID orderId, String pharmacistId) {
        MedicationOrderEntity order = load(orderId);
        Instant now = clock.instant();
        orderBO.validate(order, pharmacistId, now);
        WardEntity ward = wards.findById(order.getWardCode())
                .orElseThrow(() -> new IllegalStateException("Ward " + order.getWardCode() + " disappeared"));
        events.publish(new OrderValidatedNotice(order.getId(), pharmacistId, destinationOf(order, ward),
                order.isControlled(), now));
        log.info("Order {} validated by {}", orderId, pharmacistId);
        return OrderView.from(order);
    }

    @Transactional
    public OrderView reject(UUID orderId, String pharmacistId, String reason) {
        MedicationOrderEntity order = load(orderId);
        orderBO.reject(order, pharmacistId, reason, clock.instant());
        StockItemEntity stock = stockItems.findForUpdate(order.getDrugCode())
                .orElseThrow(() -> new IllegalStateException("No stock row for " + order.getDrugCode()));
        stockBO.release(stock, order.getQty());
        log.info("Order {} rejected by {}, {} x {} released", orderId, pharmacistId, order.getQty(),
                order.getDrugCode());
        return OrderView.from(order);
    }

    @Transactional(readOnly = true)
    public OrderView get(UUID orderId) {
        return OrderView.from(load(orderId));
    }

    @Transactional(readOnly = true)
    public PageView<OrderView> search(OrderStatus status, String ward, int page, int size) {
        PageRequest request = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return PageView.of(orders.findAll(MedicationOrderSpecifications.matching(status, ward), request),
                OrderView::from);
    }

    @Transactional(readOnly = true)
    public List<OrderView> pharmacyQueue() {
        return orders.findByStatusOrderByCreatedAtAsc(OrderStatus.PLACED).stream().map(OrderView::from).toList();
    }

    @Transactional(readOnly = true)
    public List<WardView> deliveryWards() {
        return wards.findAllByDeliverableTrueOrderByCodeAsc().stream().map(WardView::from).toList();
    }

    private MedicationOrderEntity load(UUID orderId) {
        return orders.findById(orderId).orElseThrow(() -> new NotFoundException("Order", orderId));
    }

    private static DeliveryDestination destinationOf(MedicationOrderEntity order, WardEntity ward) {
        return new DeliveryDestination(ward.getCode(), order.getRoom(), ward.getGridX(), ward.getGridY());
    }
}
