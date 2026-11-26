package tn.rnu.eniso.doserunner.dispatch.dao;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "missions")
public class Mission {
    @Id
    @Column(nullable = false)
    private UUID id;

    @Column(name = "order_id", nullable = false)
    private UUID orderId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "robot_id", nullable = false)
    private Robot robot;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private MissionStatus status;

    protected Mission() {
    }

    public Mission(UUID id, UUID orderId, Robot robot, MissionStatus status) {
        this.id = Objects.requireNonNull(id, "id");
        this.orderId = Objects.requireNonNull(orderId, "orderId");
        this.robot = Objects.requireNonNull(robot, "robot");
        setStatus(status);
    }

    public UUID getId() { return id; }
    public UUID getOrderId() { return orderId; }
    public Robot getRobot() { return robot; }
    public MissionStatus getStatus() { return status; }

    public void setStatus(MissionStatus status) {
        this.status = Objects.requireNonNull(status, "status");
    }
}
