package tn.rnu.eniso.doserunner.identity.pres.ui;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.security.Principal;

/** The form-login page. The POST to {@code /login} and {@code /logout} are handled by Spring Security. */
@Controller
public class LoginPageController {

    @GetMapping("/login")
    public String login() {
        return "ui/login";
    }

    @GetMapping("/ui")
    public String home() {
        return "redirect:/ui/admin/users";
    }

    /** Forwarded to by the UI chain when a logged-in user lacks the role (e.g. a nurse on the admin page). */
    @GetMapping("/ui/forbidden")
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public String forbidden(Model model, Principal principal) {
        model.addAttribute("me", principal == null ? null : principal.getName());
        return "ui/forbidden";
    }
}
