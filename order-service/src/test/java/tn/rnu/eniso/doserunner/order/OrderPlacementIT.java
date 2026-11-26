package tn.rnu.eniso.doserunner.order;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import tn.rnu.eniso.doserunner.order.facade.OrderFacade;
import tn.rnu.eniso.doserunner.order.facade.OrderView;
import tn.rnu.eniso.doserunner.order.facade.PlaceOrderCommand;
import tn.rnu.eniso.doserunner.order.shared.OrderStatus;
import tn.rnu.eniso.doserunner.order.shared.error.InsufficientStockException;
import tn.rnu.eniso.doserunner.order.support.ContainersSupport;
import tn.rnu.eniso.doserunner.order.support.TestStock;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class OrderPlacementIT extends ContainersSupport {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private OrderFacade orderFacade;

    @Autowired
    private JdbcTemplate jdbc;

    private TestStock stock;

    @BeforeEach
    void setUp() {
        stock = new TestStock(jdbc);
    }

    @Test
    void postOrdersReservesStockAndPersistsTheOrderInOneTransaction() throws Exception {
        String drug = stock.newDrug(false, 10);

        mvc.perform(post("/orders").contentType(MediaType.APPLICATION_JSON)
                        .header("X-User-Id", "u-nurse-1")
                        .content(orderJson(drug, 3)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", containsString("/orders/")))
                .andExpect(jsonPath("$.status").value("PLACED"))
                .andExpect(jsonPath("$.nurseId").value("u-nurse-1"))
                .andExpect(jsonPath("$.qty").value(3));

        assertThat(stock.available(drug)).isEqualTo(7);
        assertThat(stock.reserved(drug)).isEqualTo(3);
        assertThat(stock.orderCount(drug)).isEqualTo(1);
    }

    @Test
    void notEnoughStockIsA409AndChangesNothing() throws Exception {
        String drug = stock.newDrug(false, 2);

        mvc.perform(post("/orders").contentType(MediaType.APPLICATION_JSON)
                        .header("x-request-id", "it-409")
                        .content(orderJson(drug, 5)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.traceId").value("it-409"))
                .andExpect(jsonPath("$.message").value(containsString("Not enough stock")));

        assertThat(stock.available(drug)).isEqualTo(2);
        assertThat(stock.reserved(drug)).isZero();
        assertThat(stock.orderCount(drug)).isZero();
    }

    @Test
    void aFailingOrderInsertRollsTheStockReservationBack() {
        String drug = stock.newDrug(false, 10);
        String tooLongPatientRef = "P".repeat(65);

        assertThatThrownBy(() -> orderFacade.place(
                new PlaceOrderCommand(tooLongPatientRef, drug, 4, "CARDIO-2", null), "u-nurse"))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThat(stock.available(drug)).isEqualTo(10);
        assertThat(stock.reserved(drug)).isZero();
        assertThat(stock.orderCount(drug)).isZero();
    }

    @Test
    void concurrentOrdersNeverOversell() throws Exception {
        String drug = stock.newDrug(false, 5);
        int attempts = 8;
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Boolean>> results = new ArrayList<>();
        try (ExecutorService pool = Executors.newFixedThreadPool(attempts)) {
            for (int i = 0; i < attempts; i++) {
                Callable<Boolean> attempt = () -> {
                    start.await();
                    try {
                        orderFacade.place(new PlaceOrderCommand("P-RACE", drug, 1, "ER", null), "u-nurse");
                        return true;
                    } catch (InsufficientStockException e) {
                        return false;
                    }
                };
                results.add(pool.submit(attempt));
            }
            start.countDown();
            int placed = 0;
            for (Future<Boolean> result : results) {
                placed += result.get() ? 1 : 0;
            }
            assertThat(placed).isEqualTo(5);
        }

        assertThat(stock.available(drug)).isZero();
        assertThat(stock.reserved(drug)).isEqualTo(5);
        assertThat(stock.orderCount(drug)).isEqualTo(5);
    }

    @Test
    void rejectingAnOrderReleasesItsStock() throws Exception {
        String drug = stock.newDrug(false, 6);
        OrderView order = orderFacade.place(new PlaceOrderCommand("P-7", drug, 4, "ICU-1", "3"), "u-nurse");

        mvc.perform(post("/pharmacy/orders/{id}/reject", order.id()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"duplicate prescription\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.rejectReason").value("duplicate prescription"));

        assertThat(stock.available(drug)).isEqualTo(6);
        assertThat(stock.reserved(drug)).isZero();
    }

    @Test
    void validatingTwiceIsA409() throws Exception {
        String drug = stock.newDrug(true, 6);
        OrderView order = orderFacade.place(new PlaceOrderCommand("P-8", drug, 1, "CARDIO-2", null), "u-nurse");

        mvc.perform(post("/pharmacy/orders/{id}/validate", order.id()).header("X-User-Id", "u-pharma"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("VALIDATED"))
                .andExpect(jsonPath("$.pharmacistId").value("u-pharma"));
        mvc.perform(post("/pharmacy/orders/{id}/validate", order.id()))
                .andExpect(status().isConflict());

        assertThat(orderFacade.get(order.id()).status()).isEqualTo(OrderStatus.VALIDATED);
    }

    @Test
    void invalidRequestsAre400WithFieldErrors() throws Exception {
        mvc.perform(post("/orders").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"patientRef\":\"\",\"drugCode\":\"PARA-500\",\"qty\":0,\"ward\":\"ER\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.length()").value(2));
        mvc.perform(post("/orders").contentType(MediaType.APPLICATION_JSON).content("{not json"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/orders").contentType(MediaType.APPLICATION_JSON).content(orderJson("NOPE", 1)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Unknown drug code NOPE"));
        mvc.perform(get("/orders").param("status", "FLYING"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void unknownOrderIs404() throws Exception {
        mvc.perform(get("/orders/{id}", UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }

    @Test
    void ordersCanBeSearchedByStatusAndWard() throws Exception {
        String drug = stock.newDrug(false, 10);
        orderFacade.place(new PlaceOrderCommand("P-S1", drug, 1, "MAT-1", null), "u-nurse");

        mvc.perform(get("/orders").param("status", "PLACED").param("ward", "MAT-1").param("size", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.drugCode == '" + drug + "')]").exists())
                .andExpect(jsonPath("$.size").value(100));
        mvc.perform(get("/pharmacy/queue"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.drugCode == '" + drug + "')].ward").value("MAT-1"));
        mvc.perform(get("/pharmacy/stock"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.drugCode == '" + drug + "')].reserved").value(1));
    }

    private static String orderJson(String drugCode, int qty) {
        return """
                {"patientRef":"P-0042","drugCode":"%s","qty":%d,"ward":"CARDIO-2","room":"214"}
                """.formatted(drugCode, qty);
    }
}
