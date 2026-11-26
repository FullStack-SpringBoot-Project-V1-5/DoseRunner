package tn.rnu.eniso.doserunner.commons.config;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;
import tn.rnu.eniso.doserunner.commons.sec.ApiErrorSecurityHandler;
import tn.rnu.eniso.doserunner.commons.sec.DoseRunnerSecurityProperties;
import tn.rnu.eniso.doserunner.commons.sec.RolesClaim;
import tools.jackson.databind.json.JsonMapper;

/**
 * JWT resource-server setup shared by every service. It switches on as soon as a service adds
 * {@code spring-boot-starter-security-oauth2-resource-server} to its POM. It provides:
 * <ul>
 *   <li>a {@link JwtDecoder} that fetches identity-service's JWKS from {@code doserunner.security.jwks-uri} and
 *       checks the issuer (a service, or a test, can replace it by declaring its own {@code JwtDecoder} bean);</li>
 *   <li>the {@code roles} claim mapped to {@code ROLE_*} authorities;</li>
 *   <li>a stateless API filter chain: no session, no CSRF, public probes / error / JWKS, everything else
 *       needs a valid token;</li>
 *   <li>401 / 403 written as {@code ApiError} JSON with the trace id;</li>
 *   <li>{@code @EnableMethodSecurity}, so controllers use {@code @PreAuthorize("hasRole('NURSE')")}.</li>
 * </ul>
 * Runs before Boot's security auto-configurations, so Boot's default chain and generated user back off.
 */
@AutoConfiguration(beforeName = {
        "org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration",
        "org.springframework.boot.security.autoconfigure.actuate.web.servlet.ManagementWebSecurityAutoConfiguration",
        "org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration",
        "org.springframework.boot.security.oauth2.server.resource.autoconfigure.OAuth2ResourceServerAutoConfiguration"})
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass(name = {
        "org.springframework.security.web.SecurityFilterChain",
        "org.springframework.security.oauth2.jwt.JwtDecoder"})
@EnableConfigurationProperties(DoseRunnerSecurityProperties.class)
@EnableMethodSecurity
public class DoseRunnerResourceServerAutoConfiguration {

    /** Bean name a service can reuse to replace the whole API chain (it should not need to). */
    public static final String API_CHAIN = "doseRunnerApiSecurityFilterChain";

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty("doserunner.security.jwks-uri")
    public JwtDecoder jwtDecoder(DoseRunnerSecurityProperties props) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(props.jwksUri()).build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(props.issuer()));
        return decoder;
    }

    @Bean
    @ConditionalOnMissingBean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        return RolesClaim.authenticationConverter();
    }

    @Bean
    @ConditionalOnMissingBean
    public ApiErrorSecurityHandler apiErrorSecurityHandler(ObjectProvider<JsonMapper> json) {
        return new ApiErrorSecurityHandler(json.getIfAvailable(() -> JsonMapper.builder().build()));
    }

    /**
     * Ordered last: a service may add its own chains before it (identity-service's Thymeleaf form-login chain)
     * using {@code securityMatcher(...)} and a smaller {@code @Order}.
     */
    @Bean(API_CHAIN)
    @Order(Ordered.LOWEST_PRECEDENCE - 100)
    @ConditionalOnMissingBean(name = API_CHAIN)
    public SecurityFilterChain doseRunnerApiSecurityFilterChain(HttpSecurity http, DoseRunnerSecurityProperties props,
                                                                JwtAuthenticationConverter converter,
                                                                ApiErrorSecurityHandler errors) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(props.allPublicPaths()).permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(rs -> rs
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(converter))
                        .authenticationEntryPoint(errors)
                        .accessDeniedHandler(errors))
                .exceptionHandling(ex -> ex.authenticationEntryPoint(errors).accessDeniedHandler(errors));
        return http.build();
    }
}
