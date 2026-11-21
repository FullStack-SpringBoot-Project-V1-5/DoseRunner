package tn.rnu.eniso.doserunner.dispatch.config;

import jakarta.jms.ConnectionFactory;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jms.annotation.EnableJms;
import org.springframework.jms.config.DefaultJmsListenerContainerFactory;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import tn.rnu.eniso.doserunner.contracts.messaging.*;
import tn.rnu.eniso.doserunner.dispatch.bo.*;

@Configuration(proxyBeanMethods = false)
@EnableJms
@EnableScheduling
@EnableConfigurationProperties(DispatchMessagingProperties.class)
public class DispatchMessagingConfiguration {
    @Bean AvroMessageCodec avroMessageCodec() { return AvroMessageCodec.forContracts(); }
    @Bean AvroJmsMessages avroJmsMessages(AvroMessageCodec codec) { return new AvroJmsMessages(codec); }
    @Bean RoutePlanner routePlanner() { return new ManhattanRoutePlanner(); }
    @Bean JmsTemplate jmsTemplate(ConnectionFactory factory) {
        var template = new JmsTemplate(factory);
        template.setSessionTransacted(true);
        return template;
    }
    @Bean DefaultJmsListenerContainerFactory dispatchListenerFactory(ConnectionFactory factory,
            org.springframework.core.env.Environment environment) {
        var listeners = new DefaultJmsListenerContainerFactory();
        listeners.setConnectionFactory(factory);
        listeners.setSessionTransacted(true);
        listeners.setConcurrency("1");
        listeners.setAutoStartup(environment.getProperty("dispatch.messaging.listener-enabled", Boolean.class, true));
        listeners.setErrorHandler(error -> LoggerFactory.getLogger(DispatchMessagingConfiguration.class)
                .error("OrderValidated failed; JMS transaction rolled back", error));
        return listeners;
    }
}
