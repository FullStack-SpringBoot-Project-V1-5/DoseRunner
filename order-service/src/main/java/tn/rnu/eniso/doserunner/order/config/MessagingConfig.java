package tn.rnu.eniso.doserunner.order.config;

import jakarta.jms.ConnectionFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jms.core.JmsTemplate;
import tn.rnu.eniso.doserunner.contracts.messaging.AvroJmsMessages;
import tn.rnu.eniso.doserunner.contracts.messaging.AvroMessageCodec;

@Configuration(proxyBeanMethods = false)
public class MessagingConfig {

    public static final String EVENTS_JMS_TEMPLATE = "eventsJmsTemplate";

    @Bean
    public AvroMessageCodec avroMessageCodec() {
        return AvroMessageCodec.forContracts();
    }

    @Bean
    public AvroJmsMessages avroJmsMessages(AvroMessageCodec codec) {
        return new AvroJmsMessages(codec);
    }

    @Bean(EVENTS_JMS_TEMPLATE)
    public JmsTemplate eventsJmsTemplate(ConnectionFactory connectionFactory) {
        JmsTemplate template = new JmsTemplate(connectionFactory);
        template.setPubSubDomain(true);
        template.setExplicitQosEnabled(true);
        template.setDeliveryPersistent(true);
        return template;
    }
}
