package tn.rnu.eniso.doserunner.identity.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Second security chain, for the Thymeleaf pages only: session + form login + CSRF.
 * <p>
 * It matches {@code /login}, {@code /logout}, {@code /ui/**} and {@code /css/**}, and runs before the stateless JWT
 * API chain from commons ({@code doseRunnerApiSecurityFilterChain}, ordered last). Everything else
 * ({@code /auth/**}, {@code /admin/**}, {@code /.well-known/**}, actuator) still goes to the API chain, so a browser
 * session never authorizes a REST call: the API only accepts a Bearer token.
 * <p>
 * Login checks the password against the database through {@code UiUserDetailsService} (facade) and
 * Boot's BCrypt {@code PasswordEncoder} bean. A disabled user cannot log in.
 */
@Configuration(proxyBeanMethods = false)
public class UiSecurityConfiguration {

    public static final String LOGIN_PAGE = "/login";
    public static final String HOME = "/ui/admin/users";

    @Bean
    @Order(10)
    SecurityFilterChain uiSecurityFilterChain(HttpSecurity http) throws Exception {
        http
                .securityMatcher(LOGIN_PAGE, "/logout", "/ui", "/ui/**", "/css/**")
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(LOGIN_PAGE, "/css/**").permitAll()
                        .requestMatchers("/ui/admin/**").hasRole("ADMIN")
                        .anyRequest().authenticated())
                .formLogin(form -> form
                        .loginPage(LOGIN_PAGE)
                        .defaultSuccessUrl(HOME, false)
                        .permitAll())
                .logout(logout -> logout
                        .logoutSuccessUrl(LOGIN_PAGE + "?logout")
                        .permitAll())
                .exceptionHandling(ex -> ex.accessDeniedPage("/ui/forbidden"));
        return http.build();
    }
}
