package tn.rnu.eniso.doserunner.order.facade;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tn.rnu.eniso.doserunner.order.bo.FormularyBO;
import tn.rnu.eniso.doserunner.order.config.CacheConfig;
import tn.rnu.eniso.doserunner.order.dao.DrugEntity;
import tn.rnu.eniso.doserunner.order.dao.DrugRepository;
import tn.rnu.eniso.doserunner.order.shared.error.NotFoundException;

import java.time.Clock;
import java.util.List;

@Service
public class FormularyFacade {

    private static final Logger log = LoggerFactory.getLogger(FormularyFacade.class);

    private final DrugRepository drugs;
    private final FormularyBO formularyBO;
    private final Clock clock;

    public FormularyFacade(DrugRepository drugs, FormularyBO formularyBO, Clock clock) {
        this.drugs = drugs;
        this.formularyBO = formularyBO;
        this.clock = clock;
    }

    @Cacheable(cacheNames = CacheConfig.FORMULARY_CACHE, key = "'all'")
    @Transactional(readOnly = true)
    public List<DrugView> list() {
        log.debug("Loading the formulary from the database");
        return drugs.findAllByOrderByNameAsc().stream().map(DrugView::from).toList();
    }

    @Cacheable(cacheNames = CacheConfig.FORMULARY_CACHE, key = "#code")
    @Transactional(readOnly = true)
    public DrugView get(String code) {
        log.debug("Loading drug {} from the database", code);
        return DrugView.from(load(code));
    }

    @Caching(evict = {
            @CacheEvict(cacheNames = CacheConfig.FORMULARY_CACHE, key = "'all'"),
            @CacheEvict(cacheNames = CacheConfig.FORMULARY_CACHE, key = "#code")
    })
    @Transactional
    public DrugView update(String code, UpdateDrugCommand command) {
        DrugEntity drug = load(code);
        formularyBO.update(drug, command.name(), command.form(), command.controlled(), clock.instant());
        log.info("Drug {} updated: name='{}', form={}, controlled={}", code, drug.getName(), drug.getForm(),
                drug.isControlled());
        return DrugView.from(drug);
    }

    private DrugEntity load(String code) {
        return drugs.findById(code).orElseThrow(() -> new NotFoundException("Drug", code));
    }
}
