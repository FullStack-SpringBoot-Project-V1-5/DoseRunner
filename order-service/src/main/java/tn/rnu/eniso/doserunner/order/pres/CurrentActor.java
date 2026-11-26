package tn.rnu.eniso.doserunner.order.pres;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;
import tn.rnu.eniso.doserunner.order.config.ActorProperties;

import java.security.Principal;
import java.util.regex.Pattern;

@Component
public class CurrentActor {

    public static final String USER_HEADER = "X-User-Id";

    private static final Pattern USER_ID = Pattern.compile("[A-Za-z0-9._@-]{1,64}");

    private final ActorProperties properties;

    public CurrentActor(ActorProperties properties) {
        this.properties = properties;
    }

    public String nurseId(HttpServletRequest request) {
        return resolve(request, properties.demoNurseId());
    }

    public String pharmacistId(HttpServletRequest request) {
        return resolve(request, properties.demoPharmacistId());
    }

    private String resolve(HttpServletRequest request, String demoId) {
        Principal principal = request.getUserPrincipal();
        if (principal != null && principal.getName() != null && !principal.getName().isBlank()) {
            return principal.getName();
        }
        if (properties.userHeaderEnabled()) {
            String header = request.getHeader(USER_HEADER);
            if (header != null && !header.isBlank()) {
                String candidate = header.strip();
                if (!USER_ID.matcher(candidate).matches()) {
                    throw new IllegalArgumentException(USER_HEADER + " must match " + USER_ID.pattern());
                }
                return candidate;
            }
        }
        return demoId;
    }
}
