package tn.rnu.eniso.doserunner.order.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

@ConfigurationProperties("doserunner.order.formulary-cache")
public record FormularyCacheProperties(
        @DefaultValue("500") long maximumSize,
        @DefaultValue("10m") Duration timeToLive) {
}
