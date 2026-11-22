package tn.rnu.eniso.doserunner.identity.facade;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import tn.rnu.eniso.doserunner.identity.bo.CredentialPolicy;
import tn.rnu.eniso.doserunner.identity.dao.AppUser;
import tn.rnu.eniso.doserunner.identity.dao.UserRepository;
import tn.rnu.eniso.doserunner.identity.facade.dto.UserView;

import java.util.List;
import java.util.UUID;

/** User administration (ADMIN only, enforced in the controller). */
@Service
public class UserAdminFacade {

    private static final Logger log = LoggerFactory.getLogger(UserAdminFacade.class);

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final CredentialPolicy policy;

    public UserAdminFacade(UserRepository users, PasswordEncoder passwordEncoder, CredentialPolicy policy) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.policy = policy;
    }

    @Transactional(readOnly = true)
    public List<UserView> list() {
        return users.findAll(Sort.by("username")).stream().map(UserMapper::toView).toList();
    }

    @Transactional
    public UserView create(String username, String password, List<String> roleNames) {
        policy.check(username, password);
        var roles = UserMapper.parseRoles(roleNames);
        String normalized = CredentialPolicy.normalizeUsername(username);
        if (users.existsByUsername(normalized)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Username already taken: " + normalized);
        }
        AppUser saved = users.save(new AppUser(normalized, passwordEncoder.encode(password), roles));
        log.info("User '{}' created with roles {}", normalized, roles);
        return UserMapper.toView(saved);
    }

    @Transactional
    public UserView replaceRoles(UUID id, List<String> roleNames) {
        var roles = UserMapper.parseRoles(roleNames);
        AppUser user = get(id);
        user.replaceRoles(roles);
        log.info("User '{}' roles set to {}", user.getUsername(), roles);
        return UserMapper.toView(user);
    }

    @Transactional
    public UserView setEnabled(UUID id, boolean enabled) {
        AppUser user = get(id);
        user.setEnabled(enabled);
        log.info("User '{}' {}", user.getUsername(), enabled ? "enabled" : "disabled");
        return UserMapper.toView(user);
    }

    private AppUser get(UUID id) {
        return users.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No user " + id));
    }
}
