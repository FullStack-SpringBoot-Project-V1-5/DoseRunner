package tn.rnu.eniso.doserunner.order.facade;

public record UpdateDrugCommand(String name, String form, boolean controlled) {
}
