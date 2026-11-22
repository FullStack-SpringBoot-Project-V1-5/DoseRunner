package tn.rnu.eniso.doserunner.identity.facade;

import tn.rnu.eniso.doserunner.identity.dao.AppUser;
import tn.rnu.eniso.doserunner.identity.dao.Role;
import tn.rnu.eniso.doserunner.identity.facade.dto.MeView;
import tn.rnu.eniso.doserunner.identity.facade.dto.UserView;

import java.util.Collection;
import java.util.EnumSet;
import java.util.List;

/** Entity ↔ DTO conversion, so no entity ever leaves the facade. */
final class UserMapper {

    private UserMapper() {
    }

    static List<String> roleNames(AppUser user) {
        return user.getRoles().stream().map(Role::name).sorted().toList();
    }

    static UserView toView(AppUser user) {
        return new UserView(user.getId(), user.getUsername(), roleNames(user), user.isEnabled());
    }

    static MeView toMe(AppUser user) {
        return new MeView(user.getId(), user.getUsername(), roleNames(user));
    }

    /** @throws IllegalArgumentException on an empty list or an unknown role name (→ 400) */
    static EnumSet<Role> parseRoles(Collection<String> names) {
        if (names == null || names.isEmpty()) {
            throw new IllegalArgumentException("At least one role is required");
        }
        EnumSet<Role> roles = EnumSet.noneOf(Role.class);
        names.forEach(n -> roles.add(Role.parse(n)));
        return roles;
    }

    /** Usernames come from the client: keep only safe characters before logging them. */
    static String safeForLog(String username) {
        if (username == null) {
            return "<null>";
        }
        String cut = username.length() > 64 ? username.substring(0, 64) : username;
        return cut.replaceAll("[^A-Za-z0-9._-]", "?");
    }
}
