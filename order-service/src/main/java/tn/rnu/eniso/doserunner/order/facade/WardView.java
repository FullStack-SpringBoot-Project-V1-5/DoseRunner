package tn.rnu.eniso.doserunner.order.facade;

import tn.rnu.eniso.doserunner.order.dao.WardEntity;

public record WardView(String code, String name) {

    static WardView from(WardEntity ward) {
        return new WardView(ward.getCode(), ward.getName());
    }
}
