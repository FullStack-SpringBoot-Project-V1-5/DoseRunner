package tn.rnu.eniso.doserunner.order.dao;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;

@Entity
@Table(name = "drug")
public class DrugEntity {

    @Id
    @Column(name = "code", length = 32, nullable = false, updatable = false)
    private String code;

    @Column(name = "name", length = 128, nullable = false)
    private String name;

    @Column(name = "form", length = 32, nullable = false)
    private String form;

    @Column(name = "controlled", nullable = false)
    private boolean controlled;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected DrugEntity() {
    }

    public DrugEntity(String code, String name, String form, boolean controlled, Instant updatedAt) {
        this.code = code;
        this.name = name;
        this.form = form;
        this.controlled = controlled;
        this.updatedAt = updatedAt;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getForm() {
        return form;
    }

    public void setForm(String form) {
        this.form = form;
    }

    public boolean isControlled() {
        return controlled;
    }

    public void setControlled(boolean controlled) {
        this.controlled = controlled;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public long getVersion() {
        return version;
    }
}
