# OpenTelemetry

## Summary

Sample project to illustrate issue [#7576](https://github.com/open-telemetry/opentelemetry-java-instrumentation/issues/7576).

Custom tag set in method helloWorld not visible on traces; but cleanly shown on the logs.

```
var span = Span.current();
span.setAttribute("custom-tag.main", str);
log.info("HelloWorld: Trace ID: {}, Span ID: {}",  span.getSpanContext().getTraceId(),  span.getSpanContext().getSpanId());
```

Tracing works fine when removing Spring WebMVC dependency.  
In case of the demo app WebMVC is not required. However, WebMVC maybe added by integrating Spring Data REST.

```
implementation 'org.springframework.boot:spring-boot-starter-web'
```

## VM args

```
-Dotel.javaagent.configuration-file=src/main/resources/application.properties
-Dotel.resource.attributes=service.name=demp-app
-javaagent:lib/opentelemetry-javaagent.jar
```
