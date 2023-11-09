package com.example.opentelemetry;

import io.micrometer.observation.ObservationRegistry;
import io.micrometer.observation.contextpropagation.ObservationThreadLocalAccessor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.common.header.Headers;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationStartedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import reactor.core.Disposable;
import reactor.core.publisher.Mono;
import reactor.kafka.receiver.KafkaReceiver;
import reactor.kafka.receiver.ReceiverRecord;
import reactor.kafka.receiver.observation.KafkaReceiverObservation;
import reactor.kafka.receiver.observation.KafkaRecordReceiverContext;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

@Slf4j
@Component
@RequiredArgsConstructor
public class ReactiveKafkaConsumer {

    @Value("${app.kafka.properties.bootstrap.servers}")
    private String bootstrapServers;

    @Value("${app.kafka.delay}")
    private long delay;

    private final KafkaReceiver<String, Map> kafkaReceiver;

    private final ObservationRegistry observationRegistry;

    Consumer<Throwable> logGenericError =
            e -> log.error("Unhandled generic error: {}", e.getMessage(), e);

    Consumer<ReceiverRecord<String, Map>> logEventProcessing =
            receiverRecord -> log.info("Processing Kafka record, {}", printReceiverRecord(receiverRecord));

    @EventListener(ApplicationStartedEvent.class)
    public Disposable consume() {
        return kafkaReceiver
                .receive()
                .delayElements(Duration.ofMillis(delay))
                .doOnNext(logEventProcessing)
                .flatMap(receiverRecord -> {
                    var receiverObservation =
                            KafkaReceiverObservation.RECEIVER_OBSERVATION.start(null,
                                    KafkaReceiverObservation.DefaultKafkaReceiverObservationConvention.INSTANCE,
                                    () ->
                                            new KafkaRecordReceiverContext(
                                                    receiverRecord, "user.receiver",
                                                    bootstrapServers),
                                    observationRegistry);
                    return Mono.just(receiverRecord)
                            .doOnTerminate(receiverObservation::stop)
                            .doOnError(receiverObservation::error)
                            .contextWrite(context ->
                                    context.put(ObservationThreadLocalAccessor.KEY, receiverObservation));
                })
                .flatMap(this::handleReceiverRecord)
                .subscribe(
                        receiverRecord -> receiverRecord.receiverOffset().acknowledge(),
                        logGenericError
                );
    }

    private Mono<ReceiverRecord<String, Map>> handleReceiverRecord(ReceiverRecord<String, Map> receiverRecord) {
        log.info("Received record: {}", printReceiverRecord(receiverRecord));

        return Mono.just(receiverRecord);
    }

    private Map<String, String> retrieveHeader(Headers headers) {
        var headerValues = new HashMap<String, String>();

        headers.forEach(header ->
                headerValues.put(header.key(), retrieveHeaderValue(headers, header.key()))
        );

        return headerValues;
    }

    private String retrieveHeaderValue(Headers headers, String name) {
        var header = headers.lastHeader(name);
        if (header != null) {
            return new String(header.value());
        } else {
            return null;
        }
    }

    private String printReceiverRecord(ReceiverRecord<String, Map> rec) {
        return "ConsumerRecord(topic = " + rec.topic()
                + ", partition = " + rec.partition()
                + ", leaderEpoch = " + rec.leaderEpoch().orElse(null)
                + ", offset = " + rec.offset()
                + ", " + rec.timestampType() + " = " + rec.timestamp()
                + ", serialized key size = " + rec.serializedKeySize()
                + ", serialized value size = " + rec.serializedValueSize()
                + ", headers = " + retrieveHeader(rec.headers())
                + ", key = " + rec.key()
                + ", value = " + rec.value() + ")";
    }
}
