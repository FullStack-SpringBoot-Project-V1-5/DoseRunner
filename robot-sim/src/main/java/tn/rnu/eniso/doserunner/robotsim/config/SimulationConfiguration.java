package tn.rnu.eniso.doserunner.robotsim.config;

import jakarta.jms.*;
import java.util.function.LongSupplier;
import org.springframework.context.annotation.*;
import org.springframework.core.env.Environment;
import org.springframework.jms.annotation.EnableJms;
import org.springframework.jms.config.DefaultJmsListenerContainerFactory;
import org.springframework.jms.core.JmsTemplate;
import tn.rnu.eniso.doserunner.contracts.messaging.*;

@Configuration(proxyBeanMethods = false)
@EnableJms
public class SimulationConfiguration {
    @Bean AvroMessageCodec avroMessageCodec() { return AvroMessageCodec.forContracts(); }
    @Bean AvroJmsMessages avroJmsMessages(AvroMessageCodec codec) { return new AvroJmsMessages(codec); }
    @Bean LongSupplier simulationClock() { return System::nanoTime; }
    @Bean JmsTemplate jmsTemplate(ConnectionFactory factory) {
        var template = new JmsTemplate(factory);
        template.setPubSubDomain(true);
        template.setExplicitQosEnabled(true);
        template.setDeliveryMode(DeliveryMode.PERSISTENT);
        template.setSessionTransacted(true);
        return template;
    }
    @Bean DefaultJmsListenerContainerFactory simListenerFactory(ConnectionFactory factory, Environment environment) {
        var listeners = new DefaultJmsListenerContainerFactory();
        listeners.setConnectionFactory(factory);
        listeners.setSessionTransacted(true);
        listeners.setConcurrency("1");
        listeners.setAutoStartup(environment.getProperty("sim.listener-enabled", Boolean.class, true));
        listeners.setErrorHandler(error -> org.slf4j.LoggerFactory.getLogger(SimulationConfiguration.class)
                .error("MissionCommand rejected; JMS transaction rolled back", error));
        return listeners;
    }
}
