package tn.rnu.eniso.doserunner.commons.config;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import tn.rnu.eniso.doserunner.commons.sec.RateLimitFilter;
import tn.rnu.eniso.doserunner.commons.sec.RateLimitProperties;
import tools.jackson.databind.json.JsonMapper;

/** Registers {@link RateLimitFilter} in every servlet service. Disable with {@code doserunner.ratelimit.enabled=false}. */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass(name = {"io.github.bucket4j.Bucket", "com.github.benmanes.caffeine.cache.Caffeine"})
@ConditionalOnProperty(prefix = "doserunner.ratelimit", name = "enabled", matchIfMissing = true)
@EnableConfigurationProperties(RateLimitProperties.class)
public class RateLimitAutoConfiguration {

    /**
     * Spring Security's filter is registered at order -100 (SecurityProperties.DEFAULT_FILTER_ORDER);
     * running just after it means the caller is already authenticated. Without Spring Security every
     * caller is simply keyed by IP.
     */
    static final int ORDER_AFTER_SECURITY = -100 + 10;

    @Bean
    @ConditionalOnMissingBean(name = "rateLimitFilterRegistration")
    public FilterRegistrationBean<RateLimitFilter> rateLimitFilterRegistration(RateLimitProperties props,
                                                                               ObjectProvider<JsonMapper> json) {
        RateLimitFilter filter = new RateLimitFilter(props, json.getIfAvailable(() -> JsonMapper.builder().build()));
        FilterRegistrationBean<RateLimitFilter> reg = new FilterRegistrationBean<>(filter);
        reg.setOrder(ORDER_AFTER_SECURITY);
        reg.addUrlPatterns("/*");
        reg.setName("rateLimitFilter");
        return reg;
    }
}
