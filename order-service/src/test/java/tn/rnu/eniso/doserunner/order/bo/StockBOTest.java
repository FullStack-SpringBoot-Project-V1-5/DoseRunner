package tn.rnu.eniso.doserunner.order.bo;

import org.junit.jupiter.api.Test;
import tn.rnu.eniso.doserunner.order.dao.StockItemEntity;
import tn.rnu.eniso.doserunner.order.shared.error.InsufficientStockException;
import tn.rnu.eniso.doserunner.order.shared.error.InvalidOrderException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StockBOTest {

    private final StockBO stockBO = new StockBO();

    @Test
    void reserveMovesUnitsFromAvailableToReserved() {
        StockItemEntity stock = new StockItemEntity("MORPH-10", 10, 2);

        stockBO.reserve(stock, 4);

        assertThat(stock.getAvailable()).isEqualTo(6);
        assertThat(stock.getReserved()).isEqualTo(6);
    }

    @Test
    void reserveCanTakeTheLastUnit() {
        StockItemEntity stock = new StockItemEntity("MORPH-10", 3, 0);

        stockBO.reserve(stock, 3);

        assertThat(stock.getAvailable()).isZero();
        assertThat(stock.getReserved()).isEqualTo(3);
    }

    @Test
    void reserveRejectsWhenNotEnoughStockAndLeavesStockUntouched() {
        StockItemEntity stock = new StockItemEntity("FENT-50", 2, 1);

        assertThatThrownBy(() -> stockBO.reserve(stock, 3))
                .isInstanceOfSatisfying(InsufficientStockException.class, e -> {
                    assertThat(e.drugCode()).isEqualTo("FENT-50");
                    assertThat(e.requested()).isEqualTo(3);
                    assertThat(e.available()).isEqualTo(2);
                });
        assertThat(stock.getAvailable()).isEqualTo(2);
        assertThat(stock.getReserved()).isEqualTo(1);
    }

    @Test
    void reserveRejectsNonPositiveQuantities() {
        StockItemEntity stock = new StockItemEntity("PARA-500", 10, 0);

        assertThatThrownBy(() -> stockBO.reserve(stock, 0)).isInstanceOf(InvalidOrderException.class);
        assertThatThrownBy(() -> stockBO.reserve(stock, -1)).isInstanceOf(InvalidOrderException.class);
    }

    @Test
    void releaseGivesUnitsBack() {
        StockItemEntity stock = new StockItemEntity("PARA-500", 5, 4);

        stockBO.release(stock, 4);

        assertThat(stock.getAvailable()).isEqualTo(9);
        assertThat(stock.getReserved()).isZero();
    }

    @Test
    void releaseRefusesToReleaseMoreThanReserved() {
        StockItemEntity stock = new StockItemEntity("PARA-500", 5, 1);

        assertThatThrownBy(() -> stockBO.release(stock, 2)).isInstanceOf(IllegalStateException.class);
        assertThat(stock.getReserved()).isEqualTo(1);
    }
}
