package tn.rnu.eniso.doserunner.order.facade;

import tn.rnu.eniso.doserunner.order.dao.StockItemEntity;

public record StockView(String drugCode, int available, int reserved) {

    static StockView from(StockItemEntity stock) {
        return new StockView(stock.getDrugCode(), stock.getAvailable(), stock.getReserved());
    }
}
