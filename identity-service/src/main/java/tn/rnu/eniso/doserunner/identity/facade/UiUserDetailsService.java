package tn.rnu.eniso.doserunner.identity.facade;

import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tn.rnu.eniso.doserunner.identity.bo.CredentialPolicy;
import tn.rnu.eniso.doserunner.identity.dao.UserRepository;

/**
 * Users for the Thymeleaf form login ({@code /login}). Same accounts and BCrypt hashes as {@code POST /auth/login};
 * Spring Security's {@code DaoAuthenticationProvider} does the password check (and its own timing protection).
 */
@Service
public class UiUserDetailsService implements UserDetailsService {

    private final UserRepository users;

    public UiUserDetailsService(UserRepository users) {
        this.users = users;
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) {
        return users.findByUsername(CredentialPolicy.normalizeUsername(username))
                .map(u -> User.withUsername(u.getUsername())
                        .password(u.getPasswordHash())
                        .roles(UserMapper.roleNames(u).toArray(String[]::new))
                        .disabled(!u.isEnabled())
                        .build())
                .orElseThrow(() -> new UsernameNotFoundException("Unknown user"));
    }
}
