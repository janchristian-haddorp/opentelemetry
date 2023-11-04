package com.example.opentelemetry;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.observation.ObservationRegistry;
import org.apache.kafka.clients.CommonClientConfigs;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.config.SaslConfigs;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import reactor.kafka.receiver.KafkaReceiver;
import reactor.kafka.receiver.MicrometerConsumerListener;
import reactor.kafka.receiver.ReceiverOptions;

import java.util.Collections;
import java.util.Map;

@Configuration
public class ReactiveKafkaConsumerConfig {

    @Bean
    public ReceiverOptions<String, Map> kafkaReceiverOptions(
            @Value("${app.kafka.properties.bootstrap.servers}") String bootstrapServers,
            @Value("${app.kafka.properties.security.protocol}") String securityProtocol,
            @Value("${app.kafka.properties.sasl.mechanism}") String saslMechanism,
            @Value("${app.kafka.properties.sasl.jaas.config}") String saslJaasConfig,
            @Value("${app.kafka.topic}") String topic,
            @Value("${app.kafka.group}") String groupId) {
        var kafkaProperties = new KafkaProperties();
        kafkaProperties.getProperties().put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        kafkaProperties.getProperties().put(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG, securityProtocol);
        kafkaProperties.getProperties().put(SaslConfigs.SASL_MECHANISM, saslMechanism);
        kafkaProperties.getProperties().put(SaslConfigs.SASL_JAAS_CONFIG, saslJaasConfig);
        kafkaProperties.getProperties().put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        kafkaProperties.getProperties().put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");
        kafkaProperties.getProperties().put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        kafkaProperties.getProperties().put(JsonDeserializer.VALUE_DEFAULT_TYPE, Map.class.getName());
        kafkaProperties.getConsumer().setKeyDeserializer(StringDeserializer.class);
        kafkaProperties.getConsumer().setValueDeserializer(JsonDeserializer.class);

        ReceiverOptions<String, Map> receiverOptions = ReceiverOptions.create(kafkaProperties.buildConsumerProperties());
        return receiverOptions.subscription(Collections.singleton(topic));
    }

    @Bean
    public MicrometerConsumerListener micrometerConsumerListener(MeterRegistry registry) {
        return new MicrometerConsumerListener(registry);
    }

    @Bean
    public KafkaReceiver<String, Map> kafkaReceiver(
            ReceiverOptions<String, Map> kafkaReceiverOptions,
            MicrometerConsumerListener consumerListener,
            ObservationRegistry observationRegistry) {
        return KafkaReceiver.create(kafkaReceiverOptions
                .withObservation(observationRegistry)
                .consumerListener(consumerListener));
    }
}
