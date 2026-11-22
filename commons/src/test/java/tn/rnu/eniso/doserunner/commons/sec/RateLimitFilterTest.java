package tn.rnu.eniso.doserunner.commons.sec;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import tn.rnu.eniso.doserunner.commons.trace.CorrelationIdFilter;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RateLimitFilterTest {

    private static final int CAPACITY = 3;

    private final RateLimitFilter filter = new RateLimitFilter(
            new RateLimitProperties(true, CAPACITY, 1, 100, Duration.ofMinutes(10), List.of("/actuator/**")),
            JsonMapper.builder().build());
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new PingController())
            .addFilters(new CorrelationIdFilter(), filter).build();

    @Test
    void requestNPlusOneGets429WithRetryAfterAndApiError() throws Exception {
        for (int i = CAPACITY - 1; i >= 0; i--) {
            mvc.perform(get("/ping").with(ip("10.0.0.1")))
                    .andExpect(status().isOk())
                    .andExpect(header().string(RateLimitFilter.REMAINING_HEADER, String.valueOf(i)));
        }
        mvc.perform(get("/ping").with(ip("10.0.0.1")).header("x-request-id", "rl-1"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.status").value(429))
                .andExpect(jsonPath("$.traceId").value("rl-1"));
    }

    @Test
    void eachCallerHasItsOwnBucket() throws Exception {
        for (int i = 0; i < CAPACITY; i++) {
            mvc.perform(get("/ping").with(ip("10.0.0.2"))).andExpect(status().isOk());
        }
        mvc.perform(get("/ping").with(ip("10.0.0.2"))).andExpect(status().isTooManyRequests());
        mvc.perform(get("/ping").with(ip("10.0.0.3"))).andExpect(status().isOk());
        // same IP, but authenticated: keyed by user, not by IP
        mvc.perform(get("/ping").with(ip("10.0.0.2")).principal(() -> "user-42")).andExpect(status().isOk());
    }

    @Test
    void probesAreNeverLimited() throws Exception {
        for (int i = 0; i < CAPACITY + 2; i++) {
            mvc.perform(get("/actuator/health").with(ip("10.0.0.4"))).andExpect(status().isOk());
        }
    }

    @Test
    void trackedCallersAreBounded() throws Exception {
        RateLimitFilter small = new RateLimitFilter(
                new RateLimitProperties(true, 5, 5, 10, Duration.ofMinutes(10), List.of()), JsonMapper.builder().build());
        MockMvc m = MockMvcBuilders.standaloneSetup(new PingController()).addFilters(small).build();
        for (int i = 0; i < 200; i++) {
            m.perform(get("/ping").with(ip("192.168.0." + i)));
        }
        assertThat(small.trackedKeys()).isLessThanOrEqualTo(10);
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor ip(String address) {
        return request -> {
            request.setRemoteAddr(address);
            return request;
        };
    }

    @RestController
    static class PingController {
        @GetMapping({"/ping", "/actuator/health"})
        String ping() {
            return "pong";
        }
    }
}
