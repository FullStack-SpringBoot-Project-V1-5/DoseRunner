package tn.rnu.eniso.doserunner.order.facade;

public record PlaceOrderCommand(String patientRef, String drugCode, int qty, String ward, String room) {
}
