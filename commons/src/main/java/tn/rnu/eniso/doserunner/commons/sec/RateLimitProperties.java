package tn.rnu.eniso.doserunner.commons.sec;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;
import java.util.List;

/**
 * {@code doserunner.ratelimit.*}: per-caller token bucket applied by {@link RateLimitFilter}.
 *
 * @param enabled         switch it off with {@code doserunner.ratelimit.enabled=false} (e.g. a load test)
 * @param capacity        burst size: requests allowed at once
 * @param refillPerMinute steady rate: tokens added back per minute
 * @param maxKeys         at most this many callers are tracked; the least recently used are evicted beyond
 * @param idleExpiry      a caller's bucket is dropped after this long without a request
 * @param excludedPaths   never limited (probes must not be throttled)
 */
@ConfigurationProperties("doserunner.ratelimit")
public record RateLimitProperties(
        @DefaultValue("true") boolean enabled,
        @DefaultValue("200") long capacity,
        @DefaultValue("200") long refillPerMinute,
        @DefaultValue("10000") long maxKeys,
        @DefaultValue("10m") Duration idleExpiry,
        @DefaultValue("/actuator/**") List<String> excludedPaths) {

    public RateLimitProperties {
        if (capacity < 1 || refillPerMinute < 1) {
            throw new IllegalArgumentException("doserunner.ratelimit.capacity and refill-per-minute must be >= 1");
        }
        excludedPaths = excludedPaths == null ? List.of() : List.copyOf(excludedPaths);
    }
}
