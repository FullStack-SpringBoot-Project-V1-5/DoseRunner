package tn.rnu.eniso.doserunner.commons.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import tn.rnu.eniso.doserunner.commons.trace.CorrelationIdFilter;
import tn.rnu.eniso.doserunner.commons.trace.TraceHeaders;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GlobalExceptionHandlerTest {

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new DummyController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .addFilters(new CorrelationIdFilter())
                .build();
    }

    @Test
    void validationErrorBecomes400WithFieldErrorsAndTraceId() throws Exception {
        mvc.perform(post("/dummy").header(TraceHeaders.REQUEST_ID, "abc-123")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(header().string(TraceHeaders.REQUEST_ID, "abc-123"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.traceId").value("abc-123"))
                .andExpect(jsonPath("$.path").value("/dummy"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("name"));
    }

    @Test
    void responseStatusExceptionKeepsItsStatus() throws Exception {
        mvc.perform(get("/dummy/missing"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("order not found"));
    }

    @Test
    void unexpectedExceptionBecomes500WithoutLeakingDetails() throws Exception {
        mvc.perform(get("/dummy/boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").value("Unexpected error"));
    }

    record Body(@NotBlank String name) {
    }

    @RestController
    static class DummyController {
        @PostMapping("/dummy")
        String create(@Valid @RequestBody Body body) {
            return body.name();
        }

        @GetMapping("/dummy/missing")
        String missing() {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "order not found");
        }

        @GetMapping("/dummy/boom")
        String boom() {
            throw new IllegalStateException("db password is hunter2");
        }
    }
}
