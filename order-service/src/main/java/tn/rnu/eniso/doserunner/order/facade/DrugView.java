package tn.rnu.eniso.doserunner.order.facade;

import tn.rnu.eniso.doserunner.order.dao.DrugEntity;

public record DrugView(String code, String name, String form, boolean controlled) {

    static DrugView from(DrugEntity drug) {
        return new DrugView(drug.getCode(), drug.getName(), drug.getForm(), drug.isControlled());
    }
}
