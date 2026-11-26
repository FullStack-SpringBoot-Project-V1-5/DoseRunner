package tn.rnu.eniso.doserunner.order.dao;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "ward")
public class WardEntity {

    @Id
    @Column(name = "code", length = 32, nullable = false, updatable = false)
    private String code;

    @Column(name = "name", length = 128, nullable = false)
    private String name;

    @Column(name = "grid_x", nullable = false)
    private int gridX;

    @Column(name = "grid_y", nullable = false)
    private int gridY;

    @Column(name = "deliverable", nullable = false)
    private boolean deliverable;

    protected WardEntity() {
    }

    public WardEntity(String code, String name, int gridX, int gridY, boolean deliverable) {
        this.code = code;
        this.name = name;
        this.gridX = gridX;
        this.gridY = gridY;
        this.deliverable = deliverable;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public int getGridX() {
        return gridX;
    }

    public int getGridY() {
        return gridY;
    }

    public boolean isDeliverable() {
        return deliverable;
    }
}
