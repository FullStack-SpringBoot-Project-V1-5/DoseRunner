package tn.rnu.eniso.doserunner.order;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tn.rnu.eniso.doserunner.order.facade.OrderFacade;
import tn.rnu.eniso.doserunner.order.facade.PlaceOrderCommand;
import tn.rnu.eniso.doserunner.order.shared.OrderStatus;
import tn.rnu.eniso.doserunner.order.support.ContainersSupport;
import tn.rnu.eniso.doserunner.order.support.TestStock;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@SpringBootTest
@AutoConfigureMockMvc
class UiPagesIT extends ContainersSupport {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private OrderFacade orderFacade;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void nurseFormListsTheFormularyAndTheDeliveryWards() throws Exception {
        mvc.perform(get("/ui/orders/new"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Morphine sulfate 10 mg/mL")))
                .andExpect(content().string(containsString("CARDIO-2")))
                .andExpect(content().string(not(containsString("value=\"PHARMACY\""))));
    }

    @Test
    void nurseSubmitsAnOrderAndPharmacistValidatesItFromTheQueue() throws Exception {
        String drug = new TestStock(jdbc).newDrug(false, 4);

        MvcResult placed = mvc.perform(post("/ui/orders/new")
                        .param("patientRef", "P-UI-1").param("drugCode", drug).param("qty", "2")
                        .param("ward", "PEDS-3").param("room", "12"))
                .andExpect(status().is3xxRedirection())
                .andReturn();
        String location = placed.getResponse().getRedirectedUrl();
        assertThat(location).startsWith("/ui/orders/");
        UUID orderId = UUID.fromString(location.substring("/ui/orders/".length()));

        mvc.perform(get(location))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("P-UI-1")))
                .andExpect(content().string(containsString("PLACED")));
        mvc.perform(get("/ui/pharmacy/queue"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("P-UI-1")));

        mvc.perform(post("/ui/pharmacy/orders/{id}/validate", orderId))
                .andExpect(redirectedUrl("/ui/pharmacy/queue"))
                .andExpect(flash().attribute("message", containsString("validated")));

        assertThat(orderFacade.get(orderId).status()).isEqualTo(OrderStatus.VALIDATED);
    }

    @Test
    void formErrorsAreShownOnThePage() throws Exception {
        String drug = new TestStock(jdbc).newDrug(false, 1);

        mvc.perform(post("/ui/orders/new")
                        .param("patientRef", "").param("drugCode", drug).param("qty", "1").param("ward", "ER"))
                .andExpect(status().isOk())
                .andExpect(view().name("ui/order-new"))
                .andExpect(content().string(containsString("field-error")));
        mvc.perform(post("/ui/orders/new")
                        .param("patientRef", "P-UI-2").param("drugCode", drug).param("qty", "3").param("ward", "ER"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Not enough stock")));
    }

    @Test
    void rejectingWithoutAReasonShowsAnError() throws Exception {
        String drug = new TestStock(jdbc).newDrug(false, 2);
        UUID orderId = orderFacade.place(
                new PlaceOrderCommand("P-UI-3", drug, 1, "ER", null),
                "u-nurse").id();

        mvc.perform(post("/ui/pharmacy/orders/{id}/reject", orderId).param("reason", " "))
                .andExpect(redirectedUrl("/ui/pharmacy/queue"))
                .andExpect(flash().attribute("error", containsString("reason")));
        mvc.perform(post("/ui/pharmacy/orders/{id}/reject", orderId).param("reason", "wrong patient"))
                .andExpect(flash().attribute("message", containsString("rejected")));

        assertThat(orderFacade.get(orderId).status()).isEqualTo(OrderStatus.REJECTED);
    }

    @Test
    void unknownOrderPageIs404() throws Exception {
        mvc.perform(get("/ui/orders/{id}", UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(view().name("ui/not-found"));
    }
}
