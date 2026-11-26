package tn.rnu.eniso.doserunner.order;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import tn.rnu.eniso.doserunner.order.facade.OrderFacade;
import tn.rnu.eniso.doserunner.order.facade.OrderView;
import tn.rnu.eniso.doserunner.order.facade.PlaceOrderCommand;
import tn.rnu.eniso.doserunner.order.support.ContainersSupport;
import tn.rnu.eniso.doserunner.order.support.TestStock;
import tn.rnu.eniso.doserunner.testsupport.TestJwt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static tn.rnu.eniso.doserunner.order.support.AuthTokens.as;
import static tn.rnu.eniso.doserunner.order.support.AuthTokens.nurse;
import static tn.rnu.eniso.doserunner.order.support.AuthTokens.pharmacist;

/**
 * I2: order-service accepts identity-service's JWTs and enforces the README role table.
 * Tokens come from {@code TestJwt} (same claims as identity's), so identity-service does not need to run.
 */
@SpringBootTest
@AutoConfigureMockMvc
class OrderSecurityIT extends ContainersSupport {

    @Autowired
    MockMvc mvc;
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    OrderFacade orderFacade;

    private TestStock stock;

    @BeforeEach
    void setUp() {
        stock = new TestStock(jdbc);
    }

    @Test
    void nurseTokenPlacesAnOrderAs201AndTheTokenSubjectIsTheNurseId() throws Exception {
        String drug = stock.newDrug(false, 5);
        mvc.perform(post("/orders").with(nurse("3f6c-nurse-uuid")).contentType(MediaType.APPLICATION_JSON)
                        .content(orderJson(drug)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.nurseId").value("3f6c-nurse-uuid"));
    }

    @Test
    void pharmacistTokenOnPostOrdersIs403ApiErrorAndReservesNothing() throws Exception {
        String drug = stock.newDrug(false, 5);
        mvc.perform(post("/orders").with(pharmacist()).header("x-request-id", "i2-403")
                        .contentType(MediaType.APPLICATION_JSON).content(orderJson(drug)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.traceId").value("i2-403"));
        assertThat(stock.reserved(drug)).isZero();
    }

    @Test
    void noTokenIs401ApiErrorWithBearerChallenge() throws Exception {
        String drug = stock.newDrug(false, 5);
        mvc.perform(post("/orders").header("x-request-id", "i2-401")
                        .contentType(MediaType.APPLICATION_JSON).content(orderJson(drug)))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", "Bearer"))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.traceId").value("i2-401"));
        assertThat(stock.reserved(drug)).isZero();
    }

    @Test
    void expiredTokenIs401() throws Exception {
        mvc.perform(get("/orders").header("Authorization", "Bearer " + TestJwt.expired("NURSE")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void theXUserIdHeaderCannotOverrideTheTokenSubject() throws Exception {
        String drug = stock.newDrug(false, 5);
        mvc.perform(post("/orders").with(nurse("real-nurse")).header("X-User-Id", "someone-else")
                        .contentType(MediaType.APPLICATION_JSON).content(orderJson(drug)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.nurseId").value("real-nurse"));
    }

    @Test
    void pharmacyRoutesArePharmacistOnly() throws Exception {
        OrderView order = orderFacade.place(
                new PlaceOrderCommand("P-I2", stock.newDrug(false, 3), 1, "ER", null), "u-nurse");

        mvc.perform(get("/pharmacy/queue").with(nurse())).andExpect(status().isForbidden());
        mvc.perform(post("/pharmacy/orders/{id}/validate", order.id()).with(nurse()))
                .andExpect(status().isForbidden());
        mvc.perform(post("/pharmacy/orders/{id}/validate", order.id()).with(pharmacist("ph-7")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pharmacistId").value("ph-7"));
    }

    @Test
    void readRoutesFollowTheReadmeRoleTable() throws Exception {
        OrderView order = orderFacade.place(
                new PlaceOrderCommand("P-I2R", stock.newDrug(false, 3), 1, "ER", null), "u-nurse");

        mvc.perform(get("/orders/{id}", order.id()).with(as("aud-1", "AUDITOR"))).andExpect(status().isOk());
        mvc.perform(get("/orders").with(as("aud-1", "AUDITOR"))).andExpect(status().isForbidden());
        mvc.perform(get("/orders/{id}", order.id()).with(as("op-1", "OPERATOR"))).andExpect(status().isForbidden());
        mvc.perform(get("/formulary").with(as("op-1", "OPERATOR"))).andExpect(status().isOk());
    }

    @Test
    void onlyPharmacistsUpdateTheFormulary() throws Exception {
        String code = stock.newDrug(false, 1);
        String body = "{\"name\":\"X\",\"form\":\"TABLET\",\"controlled\":false}";
        mvc.perform(put("/formulary/{code}", code).with(nurse()).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
        mvc.perform(put("/formulary/{code}", code).with(pharmacist()).contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());
    }

    @Test
    void healthAndUiPagesStayReachableWithoutAToken() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
        mvc.perform(get("/ui/orders/new")).andExpect(status().isOk());
    }

    private static String orderJson(String drugCode) {
        return """
                {"patientRef":"P-I2","drugCode":"%s","qty":1,"ward":"CARDIO-2"}
                """.formatted(drugCode);
    }
}
