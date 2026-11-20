package tn.rnu.eniso.doserunner.order.pres.ui;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import tn.rnu.eniso.doserunner.order.facade.PlaceOrderCommand;

public class NewOrderForm {

    @NotBlank
    @Size(max = 64)
    private String patientRef;

    @NotBlank
    @Size(max = 32)
    private String drugCode;

    @NotNull
    @Positive
    @Max(100)
    private Integer qty = 1;

    @NotBlank
    @Size(max = 32)
    private String ward;

    @Size(max = 16)
    private String room;

    public String getPatientRef() {
        return patientRef;
    }

    public void setPatientRef(String patientRef) {
        this.patientRef = patientRef;
    }

    public String getDrugCode() {
        return drugCode;
    }

    public void setDrugCode(String drugCode) {
        this.drugCode = drugCode;
    }

    public Integer getQty() {
        return qty;
    }

    public void setQty(Integer qty) {
        this.qty = qty;
    }

    public String getWard() {
        return ward;
    }

    public void setWard(String ward) {
        this.ward = ward;
    }

    public String getRoom() {
        return room;
    }

    public void setRoom(String room) {
        this.room = room;
    }

    PlaceOrderCommand toCommand() {
        return new PlaceOrderCommand(patientRef, drugCode, qty, ward, room);
    }
}
