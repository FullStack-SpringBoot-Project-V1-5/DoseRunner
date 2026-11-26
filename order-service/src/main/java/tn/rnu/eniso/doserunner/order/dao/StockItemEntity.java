package tn.rnu.eniso.doserunner.order.dao;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "stock_item")
public class StockItemEntity {

    @Id
    @Column(name = "drug_code", length = 32, nullable = false, updatable = false)
    private String drugCode;

    @Column(name = "available", nullable = false)
    private int available;

    @Column(name = "reserved", nullable = false)
    private int reserved;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected StockItemEntity() {
    }

    public StockItemEntity(String drugCode, int available, int reserved) {
        this.drugCode = drugCode;
        this.available = available;
        this.reserved = reserved;
    }

    public String getDrugCode() {
        return drugCode;
    }

    public int getAvailable() {
        return available;
    }

    public void setAvailable(int available) {
        this.available = available;
    }

    public int getReserved() {
        return reserved;
    }

    public void setReserved(int reserved) {
        this.reserved = reserved;
    }

    public long getVersion() {
        return version;
    }
}
