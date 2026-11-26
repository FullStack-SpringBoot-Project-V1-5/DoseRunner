package tn.rnu.eniso.doserunner.custody.dao;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;
import tn.rnu.eniso.doserunner.custody.bo.LinkType;

import java.time.Instant;

/**
 * One row of the custody ledger ({@code custody_event}, migration V1). Append-only: no setters, Hibernate
 * {@link Immutable} (never flushes an UPDATE) and a database trigger that refuses UPDATE/DELETE.
 */
@Entity
@Immutable
@Table(name = "custody_event")
public class CustodyEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", nullable = false, updatable = false, length = 64)
    private String eventId;

    @Column(name = "order_id", nullable = false, updatable = false, length = 64)
    private String orderId;

    @Column(nullable = false, updatable = false)
    private long seq;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 32)
    private LinkType type;

    @Column(name = "actor_id", updatable = false, length = 64)
    private String actorId;

    @Column(name = "robot_id", updatable = false, length = 64)
    private String robotId;

    @Column(name = "payload_hash", nullable = false, updatable = false)
    private byte[] payloadHash;

    @Column(name = "prev_hash", nullable = false, updatable = false)
    private byte[] prevHash;

    @Column(nullable = false, updatable = false)
    private byte[] signature;

    @Column(nullable = false, updatable = false)
    private Instant ts;

    protected CustodyEvent() {
        // JPA
    }

    public CustodyEvent(String eventId, String orderId, long seq, LinkType type, String actorId, String robotId,
                        byte[] payloadHash, byte[] prevHash, byte[] signature, Instant ts) {
        this.eventId = eventId;
        this.orderId = orderId;
        this.seq = seq;
        this.type = type;
        this.actorId = actorId;
        this.robotId = robotId;
        this.payloadHash = payloadHash.clone();
        this.prevHash = prevHash.clone();
        this.signature = signature.clone();
        this.ts = ts;
    }

    public Long getId() {
        return id;
    }

    public String getEventId() {
        return eventId;
    }

    public String getOrderId() {
        return orderId;
    }

    public long getSeq() {
        return seq;
    }

    public LinkType getType() {
        return type;
    }

    public String getActorId() {
        return actorId;
    }

    public String getRobotId() {
        return robotId;
    }

    public byte[] getPayloadHash() {
        return payloadHash.clone();
    }

    public byte[] getPrevHash() {
        return prevHash.clone();
    }

    public byte[] getSignature() {
        return signature.clone();
    }

    public Instant getTs() {
        return ts;
    }
}
