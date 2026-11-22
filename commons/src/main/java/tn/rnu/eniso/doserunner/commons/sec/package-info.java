/**
 * Security shared by every service (owner: Mouheb).
 * <p>
 * Opt-in: add {@code spring-boot-starter-security-oauth2-resource-server} to the service POM and set
 * {@code doserunner.security.jwks-uri}. {@code DoseRunnerResourceServerAutoConfiguration} then validates identity-service
 * JWTs, maps the {@code roles} claim to {@code ROLE_*}, and answers 401/403 as {@code ApiError} JSON.
 * In tests, use {@code test-support}'s {@code TestJwt} instead of a running identity-service.
 */
package tn.rnu.eniso.doserunner.commons.sec;
