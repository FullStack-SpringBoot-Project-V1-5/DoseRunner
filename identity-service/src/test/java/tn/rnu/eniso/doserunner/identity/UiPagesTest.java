package tn.rnu.eniso.doserunner.identity;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.hamcrest.Matchers.containsString;

/** The Thymeleaf UI chain (session + form login + CSRF) next to the stateless JWT API chain. */
@IdentityIntegrationTest
class UiPagesTest {

    static final String SEED_PASSWORD = IdentityApiTest.SEED_PASSWORD;

    @Autowired
    MockMvc mvc;

    @Test
    void loginPageIsPublic() throws Exception {
        mvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("name=\"_csrf\"")))
                .andExpect(content().string(containsString("name=\"password\"")));
    }

    @Test
    void adminPageWithoutSessionRedirectsToLogin() throws Exception {
        mvc.perform(get("/ui/admin/users"))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    void seededAdminLogsInWithTheFormAndLandsOnTheUserList() throws Exception {
        MvcResult login = mvc.perform(formLogin("/login").user("admin").password(SEED_PASSWORD))
                .andExpect(authenticated().withUsername("admin").withRoles("ADMIN"))
                .andExpect(redirectedUrl("/ui/admin/users"))
                .andReturn();
        MockHttpSession session = (MockHttpSession) login.getRequest().getSession(false);

        mvc.perform(get("/ui/admin/users").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("pharmacist")))
                .andExpect(content().string(containsString("Log out")));
    }

    @Test
    void wrongPasswordGoesBackToLoginWithError() throws Exception {
        mvc.perform(formLogin("/login").user("admin").password("wrong-password-1"))
                .andExpect(unauthenticated())
                .andExpect(redirectedUrl("/login?error"));
    }

    @Test
    void loginFormWithoutCsrfTokenIsRefused() throws Exception {
        mvc.perform(post("/login").param("username", "admin").param("password", SEED_PASSWORD))
                .andExpect(status().isForbidden())
                .andExpect(unauthenticated());
    }

    @Test
    void nurseOnAdminPageGetsTheForbiddenPage() throws Exception {
        mvc.perform(get("/ui/admin/users").with(user("nurse").roles("NURSE")))
                .andExpect(status().isForbidden())
                .andExpect(forwardedUrl("/ui/forbidden"));
        mvc.perform(get("/ui/forbidden").with(user("nurse").roles("NURSE")))
                .andExpect(status().isForbidden())
                .andExpect(content().string(containsString("Access denied")))
                .andExpect(content().string(containsString("nurse")));
    }

    @Test
    void adminCreatesAUserFromTheFormWhoCanThenLogIn() throws Exception {
        mvc.perform(post("/ui/admin/users").with(user("admin").roles("ADMIN")).with(csrf())
                        .param("username", "ui-tech1").param("password", "Strong-pass-42")
                        .param("roles", "OPERATOR"))
                .andExpect(redirectedUrl("/ui/admin/users"))
                .andExpect(flash().attribute("message", "User 'ui-tech1' created."));

        mvc.perform(formLogin("/login").user("ui-tech1").password("Strong-pass-42"))
                .andExpect(authenticated().withRoles("OPERATOR"));
    }

    @Test
    void weakPasswordAndMissingRoleComeBackAsFlashErrors() throws Exception {
        mvc.perform(post("/ui/admin/users").with(user("admin").roles("ADMIN")).with(csrf())
                        .param("username", "ui-weak").param("password", "short").param("roles", "NURSE"))
                .andExpect(redirectedUrl("/ui/admin/users"))
                .andExpect(flash().attribute("error", containsString("at least 8 characters")));
        mvc.perform(post("/ui/admin/users").with(user("admin").roles("ADMIN")).with(csrf())
                        .param("username", "ui-norole").param("password", "Strong-pass-42"))
                .andExpect(flash().attribute("error", "At least one role is required"));
    }

    @Test
    void duplicateUsernameIsAFlashErrorNotA409Page() throws Exception {
        mvc.perform(post("/ui/admin/users").with(user("admin").roles("ADMIN")).with(csrf())
                        .param("username", "nurse").param("password", "Strong-pass-42").param("roles", "NURSE"))
                .andExpect(redirectedUrl("/ui/admin/users"))
                .andExpect(flash().attribute("error", "Username already taken: nurse"));
    }

    @Test
    void disabledUserCannotLogInWithTheForm() throws Exception {
        mvc.perform(post("/ui/admin/users").with(user("admin").roles("ADMIN")).with(csrf())
                .param("username", "ui-leaver").param("password", "Strong-pass-42").param("roles", "AUDITOR"));
        String id = idOf("ui-leaver");

        mvc.perform(post("/ui/admin/users/{id}/enabled", id).with(user("admin").roles("ADMIN")).with(csrf())
                        .param("enabled", "false"))
                .andExpect(flash().attribute("message", "User 'ui-leaver' disabled."));
        mvc.perform(formLogin("/login").user("ui-leaver").password("Strong-pass-42"))
                .andExpect(unauthenticated());
    }

    @Test
    void adminCannotDisableThemselvesOrDropTheirOwnAdminRole() throws Exception {
        String adminId = "00000000-0000-0000-0000-000000000005";
        mvc.perform(post("/ui/admin/users/{id}/enabled", adminId).with(user("admin").roles("ADMIN")).with(csrf())
                        .param("enabled", "false"))
                .andExpect(flash().attribute("error", "You cannot disable your own account."));
        mvc.perform(post("/ui/admin/users/{id}/roles", adminId).with(user("admin").roles("ADMIN")).with(csrf())
                        .param("roles", "AUDITOR"))
                .andExpect(flash().attribute("error", "You cannot remove your own ADMIN role."));
    }

    @Test
    void aBrowserSessionDoesNotAuthorizeTheRestApi() throws Exception {
        MvcResult login = mvc.perform(formLogin("/login").user("admin").password(SEED_PASSWORD)).andReturn();
        MockHttpSession session = (MockHttpSession) login.getRequest().getSession(false);

        mvc.perform(get("/admin/users").session(session))
                .andExpect(status().isUnauthorized());
    }

    /** Reads a user id back from the rendered admin page (the roles form action carries it). */
    private String idOf(String username) throws Exception {
        String html = mvc.perform(get("/ui/admin/users").with(user("admin").roles("ADMIN")))
                .andReturn().getResponse().getContentAsString();
        int row = html.indexOf(">" + username + "<");
        assertThat(row).as("row for %s", username).isPositive();
        int action = html.indexOf("/ui/admin/users/", row);
        return html.substring(action + "/ui/admin/users/".length(), html.indexOf('/', action + "/ui/admin/users/".length()));
    }
}
