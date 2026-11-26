package tn.rnu.eniso.doserunner.dispatch.dao;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.Objects;

@Entity
@Table(name = "robots")
public class Robot {
    @Id
    @Column(length = 64, nullable = false)
    private String id;

    @Column(nullable = false)
    private int x;

    @Column(nullable = false)
    private int y;

    @Column(nullable = false)
    private int battery;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private RobotStatus status;

    protected Robot() {
    }

    public Robot(String id, int x, int y, int battery, RobotStatus status) {
        if (id == null || id.isBlank() || id.length() > 64) {
            throw new IllegalArgumentException("Robot id must contain 1..64 nonblank characters");
        }
        this.id = id;
        setPosition(x, y);
        setBattery(battery);
        setStatus(status);
    }

    public String getId() { return id; }
    public int getX() { return x; }
    public int getY() { return y; }
    public int getBattery() { return battery; }
    public RobotStatus getStatus() { return status; }

    public void setPosition(int x, int y) {
        if (x < 0 || y < 0) {
            throw new IllegalArgumentException("Grid coordinates must be nonnegative");
        }
        this.x = x;
        this.y = y;
    }

    public void setBattery(int battery) {
        if (battery < 0 || battery > 100) {
            throw new IllegalArgumentException("Battery must be in 0..100 percent");
        }
        this.battery = battery;
    }

    public void setStatus(RobotStatus status) {
        this.status = Objects.requireNonNull(status, "status");
    }
}
