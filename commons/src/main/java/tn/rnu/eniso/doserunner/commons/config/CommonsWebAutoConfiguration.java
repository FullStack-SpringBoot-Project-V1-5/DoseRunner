package tn.rnu.eniso.doserunner.commons.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.boot.webmvc.error.ErrorAttributes;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import tn.rnu.eniso.doserunner.commons.trace.CorrelationIdFilter;
import tn.rnu.eniso.doserunner.commons.web.GlobalExceptionHandler;
import tn.rnu.eniso.doserunner.commons.web.TraceIdErrorAttributes;

/**
 * Auto-configured in every service that has {@code commons} on its classpath
 * (registered in META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports).
 * No service needs to component-scan the commons package.
 */
@AutoConfiguration(beforeName = "org.springframework.boot.webmvc.autoconfigure.error.ErrorMvcAutoConfiguration")
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class CommonsWebAutoConfiguration {

    /** First filter in the chain (before Spring Security), so even a 401 carries a trace id. */
    @Bean
    @ConditionalOnMissingBean(name = "correlationIdFilterRegistration")
    public FilterRegistrationBean<CorrelationIdFilter> correlationIdFilterRegistration() {
        FilterRegistrationBean<CorrelationIdFilter> reg = new FilterRegistrationBean<>(new CorrelationIdFilter());
        reg.setOrder(Ordered.HIGHEST_PRECEDENCE);
        reg.addUrlPatterns("/*");
        reg.setName("correlationIdFilter");
        return reg;
    }

    @Bean
    @ConditionalOnMissingBean
    public GlobalExceptionHandler globalExceptionHandler() {
        return new GlobalExceptionHandler();
    }

    /** Replaces Boot's DefaultErrorAttributes (Boot backs off when an ErrorAttributes bean exists). */
    @Bean
    @ConditionalOnMissingBean(ErrorAttributes.class)
    public TraceIdErrorAttributes traceIdErrorAttributes() {
        return new TraceIdErrorAttributes();
    }
}
