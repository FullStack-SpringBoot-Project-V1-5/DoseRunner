package tn.rnu.eniso.doserunner.commons.trace;

import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class CorrelationIdFilterTest {

    private static final String TRACE_ID = "4bf92f3577b34da6a3ce929d0e0e4736";

    @Test
    void takesTraceIdFromTraceparent() {
        assertThat(CorrelationIdFilter.resolveTraceId("00-" + TRACE_ID + "-00f067aa0ba902b7-01", "ignored"))
                .isEqualTo(TRACE_ID);
    }

    @Test
    void fallsBackToRequestIdWhenTraceparentInvalid() {
        assertThat(CorrelationIdFilter.resolveTraceId("garbage", "req-42")).isEqualTo("req-42");
        assertThat(CorrelationIdFilter.resolveTraceId("00-" + "0".repeat(32) + "-00f067aa0ba902b7-01", "req-42"))
                .isEqualTo("req-42");
    }

    @Test
    void rejectsUnsafeRequestIdAndGeneratesOne() {
        String id = CorrelationIdFilter.resolveTraceId(null, "evil\r\nX-Injected: 1");
        assertThat(id).matches("[0-9a-f]{32}");
    }

    @Test
    void generatesWhenNothingProvided() {
        assertThat(CorrelationIdFilter.resolveTraceId(null, null)).matches("[0-9a-f]{32}");
    }

    @Test
    void putsIdInMdcDuringRequestEchoesItAndCleansUp() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/x");
        req.addHeader(TraceHeaders.TRACEPARENT, "00-" + TRACE_ID + "-00f067aa0ba902b7-01");
        MockHttpServletResponse res = new MockHttpServletResponse();
        AtomicReference<String> seenInChain = new AtomicReference<>();

        new CorrelationIdFilter().doFilter(req, res,
                new MockFilterChain(new jakarta.servlet.http.HttpServlet() {
                }, (rq, rs, ch) -> seenInChain.set(MDC.get(TraceHeaders.MDC_TRACE_ID))));

        assertThat(seenInChain.get()).isEqualTo(TRACE_ID);
        assertThat(res.getHeader(TraceHeaders.REQUEST_ID)).isEqualTo(TRACE_ID);
        assertThat(MDC.get(TraceHeaders.MDC_TRACE_ID)).isNull();
    }
}
