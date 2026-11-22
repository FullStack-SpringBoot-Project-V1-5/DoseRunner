package tn.rnu.eniso.doserunner.testsupport;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Boots a tiny app with commons on the classpath and checks the shared resource-server behaviour that every
 * service inherits: 401 / 403 as ApiError with traceId, roles claim → hasRole, public probes.
 */
@SpringBootTest(classes = ResourceServerAutoConfigurationTest.App.class)
@AutoConfigureMockMvc
@Import(TestJwtConfiguration.class)
class ResourceServerAutoConfigurationTest {

    @Autowired
    MockMvc mvc;

    @Test
    void noTokenGives401ApiErrorWithTraceId() throws Exception {
        mvc.perform(get("/nurse-only").header("x-request-id", "sec-1"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", "Bearer"))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.traceId").value("sec-1"))
                .andExpect(jsonPath("$.path").value("/nurse-only"));
    }

    @Test
    void expiredTokenGives401() throws Exception {
        mvc.perform(get("/nurse-only").header("Authorization", "Bearer " + TestJwt.expired("NURSE")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void tokenSignedByAnotherKeyGives401() throws Exception {
        String forged = TestJwt.forRoles("NURSE");
        forged = forged.substring(0, forged.lastIndexOf('.') + 1) + "AAAA";
        mvc.perform(get("/nurse-only").header("Authorization", "Bearer " + forged))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void wrongRoleGives403ApiErrorWithTraceId() throws Exception {
        mvc.perform(get("/nurse-only").header("Authorization", TestJwt.bearer("PHARMACIST"))
                        .header("x-request-id", "sec-2"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.traceId").value("sec-2"));
    }

    @Test
    void rightRoleGives200AndPrincipalCarriesClaims() throws Exception {
        mvc.perform(get("/nurse-only").header("Authorization", TestJwt.bearer("NURSE")))
                .andExpect(status().isOk())
                .andExpect(content().string("hello test-user"));
    }

    @Test
    void healthIsPublic() throws Exception {
        mvc.perform(get("/actuator/health/liveness")).andExpect(status().isNotFound()); // no actuator here: 404, not 401
    }

    @SpringBootApplication
    @Import(NurseController.class)
    static class App {
    }

    @RestController
    static class NurseController {
        @GetMapping("/nurse-only")
        @PreAuthorize("hasRole('NURSE')")
        String hello(@AuthenticationPrincipal Jwt jwt) {
            return "hello " + jwt.getClaimAsString("preferred_username");
        }
    }
}
