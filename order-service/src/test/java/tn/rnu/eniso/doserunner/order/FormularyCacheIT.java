package tn.rnu.eniso.doserunner.order;

import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.cache.CacheManager;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import tn.rnu.eniso.doserunner.order.config.CacheConfig;
import tn.rnu.eniso.doserunner.order.facade.DrugView;
import tn.rnu.eniso.doserunner.order.facade.FormularyFacade;
import tn.rnu.eniso.doserunner.order.facade.UpdateDrugCommand;
import tn.rnu.eniso.doserunner.order.support.ContainersSupport;
import tn.rnu.eniso.doserunner.order.support.TestStock;

import static org.assertj.core.api.Assertions.assertThat;
import static tn.rnu.eniso.doserunner.order.support.AuthTokens.nurse;
import static tn.rnu.eniso.doserunner.order.support.AuthTokens.pharmacist;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
@AutoConfigureMockMvc
class FormularyCacheIT extends ContainersSupport {

    @Autowired
    private FormularyFacade formularyFacade;

    @Autowired
    private CacheManager cacheManager;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private MockMvc mvc;

    private Statistics statistics;

    @BeforeEach
    void setUp() {
        cacheManager.getCache(CacheConfig.FORMULARY_CACHE).invalidate();
        statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();
    }

    @Test
    void theSecondLookupOfADrugDoesNotHitTheDatabase() {
        DrugView first = formularyFacade.get("PARA-500");
        long queriesAfterFirstCall = statistics.getPrepareStatementCount();

        DrugView second = formularyFacade.get("PARA-500");

        assertThat(queriesAfterFirstCall).isPositive();
        assertThat(statistics.getPrepareStatementCount()).isEqualTo(queriesAfterFirstCall);
        assertThat(second).isEqualTo(first);
    }

    @Test
    void theSecondListingOfTheFormularyDoesNotHitTheDatabase() {
        int size = formularyFacade.list().size();
        long queriesAfterFirstCall = statistics.getPrepareStatementCount();

        assertThat(formularyFacade.list()).hasSize(size);
        assertThat(size).isGreaterThanOrEqualTo(20);
        assertThat(statistics.getPrepareStatementCount()).isEqualTo(queriesAfterFirstCall);
    }

    @Test
    void anUpdateEvictsTheCachedDrugAndTheCachedList() {
        String code = new TestStock(jdbc).newDrug(false, 1);
        formularyFacade.get(code);
        formularyFacade.list();

        formularyFacade.update(code, new UpdateDrugCommand("Renamed drug", "capsule", true));
        long queriesBeforeReload = statistics.getPrepareStatementCount();

        DrugView reloaded = formularyFacade.get(code);
        assertThat(statistics.getPrepareStatementCount()).isGreaterThan(queriesBeforeReload);
        assertThat(reloaded.name()).isEqualTo("Renamed drug");
        assertThat(reloaded.form()).isEqualTo("CAPSULE");
        assertThat(reloaded.controlled()).isTrue();
        assertThat(formularyFacade.list()).filteredOn(d -> d.code().equals(code))
                .singleElement().extracting(DrugView::name).isEqualTo("Renamed drug");
    }

    @Test
    void restEndpointsServeAndUpdateTheFormulary() throws Exception {
        String code = new TestStock(jdbc).newDrug(false, 1);

        mvc.perform(get("/formulary/{code}", code).with(nurse())).andExpect(status().isOk())
                .andExpect(jsonPath("$.controlled").value(false));
        mvc.perform(put("/formulary/{code}", code).with(pharmacist()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Updated\",\"form\":\"INJECTION\",\"controlled\":true}"))
                .andExpect(status().isOk());
        mvc.perform(get("/formulary/{code}", code).with(nurse())).andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Updated"))
                .andExpect(jsonPath("$.controlled").value(true));
        mvc.perform(get("/formulary/{code}", "NOPE").with(nurse())).andExpect(status().isNotFound());
    }
}
