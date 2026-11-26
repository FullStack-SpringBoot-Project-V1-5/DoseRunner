package tn.rnu.eniso.doserunner.dispatch.facade;

import jakarta.persistence.EntityManager;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import tn.rnu.eniso.doserunner.contracts.common.GridPoint;
import tn.rnu.eniso.doserunner.contracts.events.OrderValidated;
import tn.rnu.eniso.doserunner.contracts.events.RobotAssigned;
import tn.rnu.eniso.doserunner.contracts.robot.MissionCommand;
import tn.rnu.eniso.doserunner.contracts.messaging.AvroMessageCodec;
import tn.rnu.eniso.doserunner.dispatch.bo.RoutePlanner;
import tn.rnu.eniso.doserunner.dispatch.config.DispatchMessagingProperties;
import tn.rnu.eniso.doserunner.dispatch.dao.DispatchMessageStore;

@Service
public class OrderValidatedFacade {
    private final DispatchFacade dispatch;
    private final DispatchMessageStore store;
    private final RoutePlanner routes;
    private final DispatchMessagingProperties properties;
    private final AvroMessageCodec codec;
    private final EntityManager entityManager;

    public OrderValidatedFacade(DispatchFacade dispatch, DispatchMessageStore store, RoutePlanner routes,
                                DispatchMessagingProperties properties, AvroMessageCodec codec, EntityManager entityManager) {
        this.dispatch = dispatch; this.store = store; this.routes = routes;
        this.properties = properties; this.codec = codec; this.entityManager = entityManager;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void process(OrderValidated event, String traceId) {
        UUID eventId = uuid(event.getEventId());
        UUID orderId = uuid(event.getOrderId());
        if (event.getOccurredAt() == null || event.getPharmacistId() == null || event.getPharmacistId().isBlank()
                || event.getDestination() == null || event.getDestination().getWard() == null
                || event.getDestination().getWard().isBlank() || traceId == null || traceId.isBlank()) {
            throw new IllegalArgumentException("Missing required event fields");
        }
        var route = routes.plan(new GridPoint(properties.pharmacyX(), properties.pharmacyY()), event.getDestination().getPoint());
        String hash = hash(codec.encode(event));
        store.lock();
        var previous = store.eventHashes(eventId);
        if (!previous.isEmpty()) {
            if (!previous.getFirst().equals(hash)) throw new IllegalArgumentException("Conflicting eventId reuse");
            return;
        }
        var mission = dispatch.assignMission(orderId, properties.pharmacyX(), properties.pharmacyY())
                .orElseThrow(() -> new IllegalStateException("No eligible robot; retry then DLQ"));
        if (!store.hasPublication(mission.getId())) {
            String robotId = mission.getRobot().getId();
            String compartment = properties.compartments().get(robotId);
            if (compartment == null) throw new IllegalStateException("Missing compartment mapping for " + robotId);
            int sequence = store.nextSequence(robotId);
            Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
            var assigned = RobotAssigned.newBuilder().setEventId(UUID.randomUUID().toString()).setOccurredAt(now)
                    .setOrderId(orderId.toString()).setRobotId(robotId).setMissionId(mission.getId().toString())
                    .setEtaSeconds(route.etaSeconds()).setPath(route.path()).build();
            var command = MissionCommand.newBuilder().setMissionId(mission.getId().toString()).setOrderId(orderId.toString())
                    .setRobotId(robotId).setSeq(sequence).setWaypoints(route.path().subList(1, route.path().size()))
                    .setCompartmentCode(compartment).setIssuedAt(now).build();
            entityManager.flush();
            store.stage(mission.getId(), orderId, robotId, sequence, traceId, codec.encode(assigned), codec.encode(command));
        }
        store.recordEvent(eventId, orderId, hash);
    }

    public static UUID uuid(String value) {
        UUID parsed = UUID.fromString(value);
        if (!parsed.toString().equalsIgnoreCase(value)) throw new IllegalArgumentException("Noncanonical UUID");
        return parsed;
    }

    private static String hash(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
}
