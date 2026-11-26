package tn.rnu.eniso.doserunner.identity.pres.ui;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import tn.rnu.eniso.doserunner.identity.facade.UserAdminFacade;
import tn.rnu.eniso.doserunner.identity.facade.dto.UserView;

import java.security.Principal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * {@code /ui/admin/users}: the Thymeleaf face of {@code /admin/users}. Same facade, same rules (CredentialPolicy,
 * 409 on a taken username); errors come back as a flash message instead of an ApiError.
 * An admin cannot disable their own account or remove their own ADMIN role (so they cannot lock themselves out).
 */
@Controller
@RequestMapping("/ui/admin/users")
@PreAuthorize("hasRole('ADMIN')")
public class UserAdminPageController {

    private static final String REDIRECT = "redirect:/ui/admin/users";
    private static final String ADMIN = "ADMIN";

    private final UserAdminFacade admin;

    public UserAdminPageController(UserAdminFacade admin) {
        this.admin = admin;
    }

    @GetMapping
    public String page(Model model, Principal principal) {
        model.addAttribute("users", admin.list());
        model.addAttribute("roles", admin.availableRoles());
        model.addAttribute("me", principal.getName());
        return "ui/admin-users";
    }

    @PostMapping
    public String create(@RequestParam(defaultValue = "") String username,
                         @RequestParam(defaultValue = "") String password,
                         @RequestParam(required = false) List<String> roles,
                         RedirectAttributes redirect) {
        return run(redirect, () -> "User '" + admin.create(username, password, roles).username() + "' created.");
    }

    @PostMapping("/{id}/roles")
    public String roles(@PathVariable UUID id, @RequestParam(required = false) List<String> roles,
                        Principal principal, RedirectAttributes redirect) {
        if (isSelf(id, principal) && (roles == null || !roles.contains(ADMIN))) {
            redirect.addFlashAttribute("error", "You cannot remove your own ADMIN role.");
            return REDIRECT;
        }
        return run(redirect, () -> {
            UserView u = admin.replaceRoles(id, roles);
            return "Roles of '" + u.username() + "' set to " + String.join(", ", u.roles()) + ".";
        });
    }

    @PostMapping("/{id}/enabled")
    public String enabled(@PathVariable UUID id, @RequestParam boolean enabled,
                          Principal principal, RedirectAttributes redirect) {
        if (!enabled && isSelf(id, principal)) {
            redirect.addFlashAttribute("error", "You cannot disable your own account.");
            return REDIRECT;
        }
        return run(redirect, () -> {
            UserView u = admin.setEnabled(id, enabled);
            return "User '" + u.username() + "' " + (u.enabled() ? "enabled" : "disabled") + ".";
        });
    }

    private boolean isSelf(UUID id, Principal principal) {
        Optional<UserView> target = admin.list().stream().filter(u -> u.id().equals(id)).findFirst();
        return target.map(u -> u.username().equals(principal.getName())).orElse(false);
    }

    /** Runs a facade call and turns its outcome into a flash message (rule violation, 404, 409 → error). */
    private static String run(RedirectAttributes redirect, Supplier<String> action) {
        try {
            redirect.addFlashAttribute("message", action.get());
        } catch (IllegalArgumentException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        } catch (ResponseStatusException e) {
            redirect.addFlashAttribute("error", e.getReason());
        }
        return REDIRECT;
    }
}
