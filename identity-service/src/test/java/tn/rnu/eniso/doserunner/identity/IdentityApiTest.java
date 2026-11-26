package tn.rnu.eniso.doserunner.identity;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The identity REST API end to end: Flyway schema + seed users on a real Postgres, real BCrypt, real RS256. */
@IdentityIntegrationTest
class IdentityApiTest {

    static final String SEED_PASSWORD = "DoseRunner-2026";

    @Autowired
    MockMvc mvc;
    @Autowired
    JsonMapper json;

    @Test
    void seededNurseLogsInAndSeesHerself() throws Exception {
        String token = login("nurse", SEED_PASSWORD);
        mvc.perform(get("/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("00000000-0000-0000-0000-000000000001"))
                .andExpect(jsonPath("$.username").value("nurse"))
                .andExpect(jsonPath("$.roles[0]").value("NURSE"));
    }

    @Test
    void loginIsCaseInsensitiveOnUsernameAndReturnsBearer15Min() throws Exception {
        mvc.perform(loginRequest("NURSE", SEED_PASSWORD))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(900));
    }

    @Test
    void wrongPasswordAndUnknownUserGiveTheSame401WithTraceId() throws Exception {
        mvc.perform(loginRequest("nurse", "wrong-password-1").header("x-request-id", "login-bad-1"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid username or password"))
                .andExpect(jsonPath("$.traceId").value("login-bad-1"));
        mvc.perform(loginRequest("nobody", "wrong-password-1"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid username or password"));
    }

    @Test
    void blankLoginBodyGives400() throws Exception {
        mvc.perform(loginRequest("", "")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.length()").value(2));
    }

    @Test
    void nurseTokenOnAdminRouteGives403() throws Exception {
        mvc.perform(get("/admin/users").header("Authorization", "Bearer " + login("nurse", SEED_PASSWORD)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    void noTokenGives401() throws Exception {
        mvc.perform(get("/auth/me")).andExpect(status().isUnauthorized());
        mvc.perform(get("/admin/users")).andExpect(status().isUnauthorized());
    }

    @Test
    void adminManagesAUserLifecycle() throws Exception {
        String admin = "Bearer " + login("admin", SEED_PASSWORD);

        String created = mvc.perform(post("/admin/users").header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"Nurse.Two\",\"password\":\"Cardio-ward-42\",\"roles\":[\"nurse\"]}"))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.username").value("nurse.two"))
                .andExpect(jsonPath("$.roles[0]").value("NURSE"))
                .andExpect(jsonPath("$.enabled").value(true))
                .andReturn().getResponse().getContentAsString();
        String id = json.readTree(created).get("id").asString();

        login("nurse.two", "Cardio-ward-42"); // the new user can log in

        mvc.perform(put("/admin/users/" + id + "/roles").header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"roles\":[\"PHARMACIST\",\"AUDITOR\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles[0]").value("AUDITOR"))
                .andExpect(jsonPath("$.roles[1]").value("PHARMACIST"));

        mvc.perform(patch("/admin/users/" + id).header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"enabled\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false));
        mvc.perform(loginRequest("nurse.two", "Cardio-ward-42")).andExpect(status().isUnauthorized());

        mvc.perform(get("/admin/users").header("Authorization", admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.username == 'nurse.two')].enabled").value(false))
                .andExpect(jsonPath("$[0].passwordHash").doesNotExist());
    }

    @Test
    void weakPasswordDuplicateAndUnknownRoleAreRejected() throws Exception {
        String admin = "Bearer " + login("admin", SEED_PASSWORD);
        mvc.perform(createUser(admin, "weakling", "short", "NURSE"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("at least 8")));
        mvc.perform(createUser(admin, "nurse", "Another-pass-1", "NURSE"))
                .andExpect(status().isConflict());
        mvc.perform(createUser(admin, "someone", "Another-pass-1", "SURGEON"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Unknown role: SURGEON"));
    }

    @Test
    void unknownUserIdGives404() throws Exception {
        mvc.perform(patch("/admin/users/00000000-0000-0000-0000-0000000000ff")
                        .header("Authorization", "Bearer " + login("admin", SEED_PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"enabled\":true}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void publishedJwksIsPublicHasNoPrivatePartAndVerifiesIssuedTokens() throws Exception {
        String body = mvc.perform(get("/.well-known/jwks.json"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.keys[0].kty").value("RSA"))
                .andExpect(jsonPath("$.keys[0].alg").value("RS256"))
                .andExpect(jsonPath("$.keys[0].d").doesNotExist())
                .andReturn().getResponse().getContentAsString();

        // What order/dispatch/custody do: verify a token using only the published JWKS.
        RSAKey published = JWKSet.parse(body).getKeys().getFirst().toRSAKey();
        Jwt jwt = NimbusJwtDecoder.withPublicKey(published.toRSAPublicKey()).build()
                .decode(login("pharmacist", SEED_PASSWORD));
        assertThat(jwt.getHeaders()).containsEntry("kid", published.getKeyID());
        assertThat(jwt.getClaimAsString("iss")).isEqualTo("doserunner-identity");
        assertThat(jwt.getSubject()).isEqualTo("00000000-0000-0000-0000-000000000002");
        assertThat(jwt.getClaimAsStringList("roles")).containsExactly("PHARMACIST");
        assertThat(jwt.getClaimAsString("preferred_username")).isEqualTo("pharmacist");
    }

    private String login(String username, String password) throws Exception {
        String body = mvc.perform(loginRequest(username, password))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode node = json.readTree(body);
        return node.get("accessToken").asString();
    }

    private static MockHttpServletRequestBuilder loginRequest(String username, String password) {
        return post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}");
    }

    private static MockHttpServletRequestBuilder createUser(String auth, String username, String password, String role) {
        return post("/admin/users").header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"" + username + "\",\"password\":\"" + password
                        + "\",\"roles\":[\"" + role + "\"]}");
    }
}
