package tn.rnu.eniso.doserunner.order.facade;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tn.rnu.eniso.doserunner.order.dao.StockItemRepository;

import java.util.List;

@Service
public class StockFacade {

    private final StockItemRepository stockItems;

    public StockFacade(StockItemRepository stockItems) {
        this.stockItems = stockItems;
    }

    @Transactional(readOnly = true)
    public List<StockView> list() {
        return stockItems.findAllByOrderByDrugCodeAsc().stream().map(StockView::from).toList();
    }
}
