package tn.rnu.eniso.doserunner.commons.sec;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;
import tn.rnu.eniso.doserunner.commons.dto.ApiError;
import tn.rnu.eniso.doserunner.commons.trace.TraceHeaders;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.security.Principal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Servlet-side rate limiting: one token bucket per caller, keyed by the authenticated user (JWT {@code sub})
 * or, for anonymous calls such as {@code POST /auth/login}, by client IP. Over the limit → 429 + {@code Retry-After}
 * + {@code ApiError}. The Istio gateway adds a coarse global limit in week 3; this one is per user.
 * <p>
 * Registered right after Spring Security's filter chain, so {@link HttpServletRequest#getUserPrincipal()} is already
 * the authenticated JWT. Buckets live in a size-bounded, expiring Caffeine cache: memory cannot grow without limit
 * when many different IPs call. Limits are per instance (not shared between replicas).
 */
public class RateLimitFilter extends OncePerRequestFilter {

    public static final String REMAINING_HEADER = "X-RateLimit-Remaining";
    private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);

    private final RateLimitProperties props;
    private final JsonMapper json;
    private final Cache<String, Bucket> buckets;
    private final AntPathMatcher paths = new AntPathMatcher();

    public RateLimitFilter(RateLimitProperties props, JsonMapper json) {
        this.props = props;
        this.json = json;
        this.buckets = Caffeine.newBuilder()
                .maximumSize(props.maxKeys())
                .expireAfterAccess(props.idleExpiry())
                .build();
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return props.excludedPaths().stream().anyMatch(p -> paths.match(p, path));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        String key = keyOf(req);
        ConsumptionProbe probe = buckets.get(key, k -> newBucket()).tryConsumeAndReturnRemaining(1);
        if (probe.isConsumed()) {
            res.setHeader(REMAINING_HEADER, Long.toString(probe.getRemainingTokens()));
            chain.doFilter(req, res);
            return;
        }
        long retryAfter = Math.max(1, TimeUnit.NANOSECONDS.toSeconds(probe.getNanosToWaitForRefill() + 999_999_999L));
        log.warn("429 for {} on {} {} (retry after {}s)", key, req.getMethod(), req.getRequestURI(), retryAfter);
        res.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        res.setHeader(HttpHeaders.RETRY_AFTER, Long.toString(retryAfter));
        res.setHeader(REMAINING_HEADER, "0");
        res.setContentType(MediaType.APPLICATION_JSON_VALUE);
        json.writeValue(res.getOutputStream(), new ApiError(Instant.now(), 429,
                HttpStatus.TOO_MANY_REQUESTS.getReasonPhrase(), "Rate limit exceeded, retry in " + retryAfter + "s",
                req.getRequestURI(), MDC.get(TraceHeaders.MDC_TRACE_ID), List.of()));
    }

    /** {@code user:<sub>} when authenticated, otherwise {@code ip:<remote address>}. */
    static String keyOf(HttpServletRequest req) {
        Principal user = req.getUserPrincipal();
        return user != null && user.getName() != null ? "user:" + user.getName() : "ip:" + req.getRemoteAddr();
    }

    private Bucket newBucket() {
        return Bucket.builder()
                .addLimit(limit -> limit.capacity(props.capacity())
                        .refillGreedy(props.refillPerMinute(), Duration.ofMinutes(1)))
                .build();
    }

    /** Number of callers currently tracked (for tests and metrics). */
    long trackedKeys() {
        buckets.cleanUp();
        return buckets.estimatedSize();
    }
}
