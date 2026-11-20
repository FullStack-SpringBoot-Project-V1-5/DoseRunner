package tn.rnu.eniso.doserunner.order.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("doserunner.order.actor")
public record ActorProperties(
        @DefaultValue("true") boolean userHeaderEnabled,
        @DefaultValue("u-nurse-demo") String demoNurseId,
        @DefaultValue("u-pharmacist-demo") String demoPharmacistId) {
}
