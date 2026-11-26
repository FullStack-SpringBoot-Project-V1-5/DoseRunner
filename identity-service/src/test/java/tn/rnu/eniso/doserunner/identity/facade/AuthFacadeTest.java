package tn.rnu.eniso.doserunner.identity.facade;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;
import tn.rnu.eniso.doserunner.identity.bo.TokenIssuer;
import tn.rnu.eniso.doserunner.identity.dao.AppUser;
import tn.rnu.eniso.doserunner.identity.dao.Role;
import tn.rnu.eniso.doserunner.identity.dao.UserRepository;
import tn.rnu.eniso.doserunner.identity.facade.dto.TokenResponse;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthFacadeTest {

    private final UserRepository users = mock(UserRepository.class);
    private final PasswordEncoder encoder = mock(PasswordEncoder.class);
    private final TokenIssuer issuer = mock(TokenIssuer.class);
    private AuthFacade facade;
    private AppUser nurse;

    @BeforeEach
    void setUp() {
        when(encoder.encode(anyString())).thenReturn("dummy-hash");
        facade = new AuthFacade(users, encoder, issuer);
        nurse = new AppUser("nurse", "nurse-hash", List.of(Role.NURSE));
        org.springframework.test.util.ReflectionTestUtils.setField(nurse, "id", UUID.randomUUID());
    }

    @Test
    void goodCredentialsGiveABearerToken() {
        when(users.findByUsername("nurse")).thenReturn(Optional.of(nurse));
        when(encoder.matches("secret-1", "nurse-hash")).thenReturn(true);
        when(issuer.issue(nurse.getId().toString(), "nurse", List.of("NURSE")))
                .thenReturn(new TokenIssuer.IssuedToken("jwt", Duration.ofMinutes(15)));

        TokenResponse res = facade.login("  Nurse ", "secret-1");

        assertThat(res).isEqualTo(new TokenResponse("jwt", "Bearer", 900));
    }

    @Test
    void wrongPasswordIs401() {
        when(users.findByUsername("nurse")).thenReturn(Optional.of(nurse));
        when(encoder.matches("bad", "nurse-hash")).thenReturn(false);
        assertUnauthorized(() -> facade.login("nurse", "bad"));
    }

    @Test
    void unknownUserIs401AndStillPaysOneHashCheck() {
        when(users.findByUsername("ghost")).thenReturn(Optional.empty());
        assertUnauthorized(() -> facade.login("ghost", "whatever"));
        verify(encoder).matches("whatever", "dummy-hash");
    }

    @Test
    void disabledUserIs401EvenWithTheRightPassword() {
        nurse.setEnabled(false);
        when(users.findByUsername("nurse")).thenReturn(Optional.of(nurse));
        when(encoder.matches("secret-1", "nurse-hash")).thenReturn(true);
        assertUnauthorized(() -> facade.login("nurse", "secret-1"));
        verify(issuer, never()).issue(any(), any(), any());
    }

    @Test
    void meForADeletedUserIs404() {
        UUID id = UUID.randomUUID();
        when(users.findById(eq(id))).thenReturn(Optional.empty());
        assertThatThrownBy(() -> facade.me(id)).isInstanceOfSatisfying(ResponseStatusException.class,
                e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    private static void assertUnauthorized(org.assertj.core.api.ThrowableAssert.ThrowingCallable call) {
        assertThatThrownBy(call).isInstanceOfSatisfying(ResponseStatusException.class, e -> {
            assertThat(e.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
            assertThat(e.getReason()).isEqualTo(AuthFacade.INVALID_CREDENTIALS);
        });
    }
}
