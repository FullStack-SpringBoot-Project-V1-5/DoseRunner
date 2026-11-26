package tn.rnu.eniso.doserunner.dispatch.config;

import java.util.ServiceLoader;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tn.rnu.eniso.doserunner.dispatch.bo.DispatchStrategy;
import tn.rnu.eniso.doserunner.dispatch.bo.NearestIdleRobotStrategy;

@Configuration
public class DispatchConfiguration {
    @Bean
    DispatchStrategy dispatchStrategy() {
        return ServiceLoader.load(DispatchStrategy.class).stream()
                .filter(provider -> provider.type() == NearestIdleRobotStrategy.class)
                .findFirst().orElseThrow().get();
    }
}
