package tn.rnu.eniso.doserunner.order.dao;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import tn.rnu.eniso.doserunner.order.shared.OrderStatus;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "medication_order")
public class MedicationOrderEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "patient_ref", length = 64, nullable = false, updatable = false)
    private String patientRef;

    @Column(name = "drug_code", length = 32, nullable = false, updatable = false)
    private String drugCode;

    @Column(name = "qty", nullable = false, updatable = false)
    private int qty;

    @Column(name = "controlled", nullable = false, updatable = false)
    private boolean controlled;

    @Column(name = "ward_code", length = 32, nullable = false, updatable = false)
    private String wardCode;

    @Column(name = "room", length = 16, updatable = false)
    private String room;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 24, nullable = false)
    private OrderStatus status;

    @Column(name = "nurse_id", length = 64, nullable = false, updatable = false)
    private String nurseId;

    @Column(name = "pharmacist_id", length = 64)
    private String pharmacistId;

    @Column(name = "reject_reason", length = 512)
    private String rejectReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    protected MedicationOrderEntity() {
    }

    public MedicationOrderEntity(UUID id, String patientRef, String drugCode, int qty, boolean controlled,
                                 String wardCode, String room, String nurseId, Instant createdAt) {
        this.id = id;
        this.patientRef = patientRef;
        this.drugCode = drugCode;
        this.qty = qty;
        this.controlled = controlled;
        this.wardCode = wardCode;
        this.room = room;
        this.nurseId = nurseId;
        this.status = OrderStatus.PLACED;
        this.createdAt = createdAt;
        this.updatedAt = createdAt;
    }

    public UUID getId() {
        return id;
    }

    public String getPatientRef() {
        return patientRef;
    }

    public String getDrugCode() {
        return drugCode;
    }

    public int getQty() {
        return qty;
    }

    public boolean isControlled() {
        return controlled;
    }

    public String getWardCode() {
        return wardCode;
    }

    public String getRoom() {
        return room;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public void setStatus(OrderStatus status) {
        this.status = status;
    }

    public String getNurseId() {
        return nurseId;
    }

    public String getPharmacistId() {
        return pharmacistId;
    }

    public void setPharmacistId(String pharmacistId) {
        this.pharmacistId = pharmacistId;
    }

    public String getRejectReason() {
        return rejectReason;
    }

    public void setRejectReason(String rejectReason) {
        this.rejectReason = rejectReason;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public Long getVersion() {
        return version;
    }
}
