package tn.rnu.eniso.doserunner.dispatch.config;

import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("dispatch.messaging")
public record DispatchMessagingProperties(int pharmacyX, int pharmacyY, Map<String, String> compartments) {
    public DispatchMessagingProperties {
        if (pharmacyX < 0 || pharmacyY < 0) throw new IllegalArgumentException("Invalid pharmacy coordinates");
        compartments = compartments == null ? Map.of() : Map.copyOf(compartments);
        if (compartments.entrySet().stream().anyMatch(e -> e.getKey().isBlank() || e.getValue().isBlank())) {
            throw new IllegalArgumentException("Compartment mappings must be nonblank");
        }
    }
}
