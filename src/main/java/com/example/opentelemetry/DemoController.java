package com.example.opentelemetry;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;

import io.opentelemetry.api.trace.Span;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@RestController
@RequiredArgsConstructor
@Slf4j
public class DemoController {

    private final WebClient.Builder builder;

    @GetMapping("/")
    public Mono<String> helloWorld() {
        return builder.baseUrl("http://localhost:8080").build()
                .get()
                .uri("/internal")
                .retrieve().bodyToMono(String.class)
                .handle((str, sink) -> {
                    var span = Span.current();
                    span.setAttribute("custom-tag.main", str);
                    log.info("HelloWorld: Trace ID: {}, Span ID: {}",  span.getSpanContext().getTraceId(),  span.getSpanContext().getSpanId());
                    sink.next(str);
                });
    }

    @GetMapping("/internal")
    public Mono<String> helloWorldInternal() {
        return Mono
                .just("Hello World!")
                .handle((str, sink) -> {
                    var span = Span.current();
                    span.setAttribute("custom-tag.internal", str);
                    log.info("HelloWorldInternal: Trace ID: {}, Span ID: {}",  span.getSpanContext().getTraceId(),  span.getSpanContext().getSpanId());
                    sink.next(str);
                });
    }
}
