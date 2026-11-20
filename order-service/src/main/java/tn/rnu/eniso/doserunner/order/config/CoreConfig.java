package tn.rnu.eniso.doserunner.order.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(ActorProperties.class)
public class CoreConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
