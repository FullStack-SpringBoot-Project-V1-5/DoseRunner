package tn.rnu.eniso.doserunner.identity.facade;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import tn.rnu.eniso.doserunner.identity.bo.CredentialPolicy;
import tn.rnu.eniso.doserunner.identity.bo.TokenIssuer;
import tn.rnu.eniso.doserunner.identity.dao.AppUser;
import tn.rnu.eniso.doserunner.identity.dao.UserRepository;
import tn.rnu.eniso.doserunner.identity.facade.dto.MeView;
import tn.rnu.eniso.doserunner.identity.facade.dto.TokenResponse;

import java.util.Optional;
import java.util.UUID;

/** Login and "who am I". */
@Service
public class AuthFacade {

    private static final Logger log = LoggerFactory.getLogger(AuthFacade.class);
    static final String INVALID_CREDENTIALS = "Invalid username or password";

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final TokenIssuer tokenIssuer;
    /** Compared against when the user does not exist, so both failure paths cost one BCrypt check (no timing oracle). */
    private final String dummyHash;

    public AuthFacade(UserRepository users, PasswordEncoder passwordEncoder, TokenIssuer tokenIssuer) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.tokenIssuer = tokenIssuer;
        this.dummyHash = passwordEncoder.encode("not-a-real-password-0");
    }

    @Transactional(readOnly = true)
    public TokenResponse login(String username, String password) {
        Optional<AppUser> found = users.findByUsername(CredentialPolicy.normalizeUsername(username));
        boolean passwordOk = passwordEncoder.matches(password, found.map(AppUser::getPasswordHash).orElse(dummyHash));
        if (found.isEmpty() || !passwordOk || !found.get().isEnabled()) {
            log.info("Login failed for '{}' ({})", UserMapper.safeForLog(username),
                    found.isEmpty() ? "unknown user" : !passwordOk ? "bad password" : "disabled");
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, INVALID_CREDENTIALS);
        }
        AppUser user = found.get();
        TokenIssuer.IssuedToken token = tokenIssuer.issue(user.getId().toString(), user.getUsername(),
                UserMapper.roleNames(user));
        log.info("Login ok for '{}' roles={}", user.getUsername(), UserMapper.roleNames(user));
        return new TokenResponse(token.value(), "Bearer", token.expiresIn().toSeconds());
    }

    @Transactional(readOnly = true)
    public MeView me(UUID userId) {
        return users.findById(userId).map(UserMapper::toMe)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User no longer exists"));
    }
}
