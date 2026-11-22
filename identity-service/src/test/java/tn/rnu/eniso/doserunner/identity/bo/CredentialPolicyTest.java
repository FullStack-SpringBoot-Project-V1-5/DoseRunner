package tn.rnu.eniso.doserunner.identity.bo;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CredentialPolicyTest {

    private final CredentialPolicy policy = new CredentialPolicy();

    @Test
    void acceptsAGoodPair() {
        assertThat(policy.violations("nurse.two", "Cardio-ward-42")).isEmpty();
    }

    @ParameterizedTest(name = "{0} / {1} → {2}")
    @CsvSource({
            "nurse,      Ab1,                at least 8",
            "nurse,      onlyletters,        letters and digits",
            "nurse,      1234567890,         letters and digits",
            "nurse,      MyNurse2026,        must not contain the username",
            "ab,         Cardio-ward-42,     username must be",
            "'Bad Name', Cardio-ward-42,     username must be",
    })
    void rejects(String username, String password, String expected) {
        assertThat(policy.violations(username, password)).anySatisfy(v -> assertThat(v).contains(expected));
    }

    @Test
    void rejectsPasswordsLongerThanBcryptAccepts() {
        assertThat(policy.violations("nurse", "a1".repeat(40))).anySatisfy(v -> assertThat(v).contains("at most 72"));
    }

    @Test
    void checkListsEveryBrokenRule() {
        assertThatThrownBy(() -> policy.check("x", "nodigits"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("username must be")
                .hasMessageContaining("letters and digits");
    }

    @Test
    void usernamesAreNormalized() {
        assertThat(CredentialPolicy.normalizeUsername("  Nurse.Two ")).isEqualTo("nurse.two");
        assertThat(CredentialPolicy.normalizeUsername(null)).isEmpty();
    }
}
