package tn.rnu.eniso.doserunner.order.bo;

import org.springframework.stereotype.Component;
import tn.rnu.eniso.doserunner.order.dao.StockItemEntity;
import tn.rnu.eniso.doserunner.order.shared.error.InsufficientStockException;
import tn.rnu.eniso.doserunner.order.shared.error.InvalidOrderException;

@Component
public class StockBO {

    public void reserve(StockItemEntity stock, int qty) {
        requirePositive(qty);
        if (stock.getAvailable() < qty) {
            throw new InsufficientStockException(stock.getDrugCode(), qty, stock.getAvailable());
        }
        stock.setAvailable(stock.getAvailable() - qty);
        stock.setReserved(stock.getReserved() + qty);
    }

    public void release(StockItemEntity stock, int qty) {
        requirePositive(qty);
        if (stock.getReserved() < qty) {
            throw new IllegalStateException("Cannot release " + qty + " units of " + stock.getDrugCode()
                    + ": only " + stock.getReserved() + " reserved");
        }
        stock.setReserved(stock.getReserved() - qty);
        stock.setAvailable(stock.getAvailable() + qty);
    }

    private static void requirePositive(int qty) {
        if (qty <= 0) {
            throw new InvalidOrderException("Quantity must be positive, got " + qty);
        }
    }
}
