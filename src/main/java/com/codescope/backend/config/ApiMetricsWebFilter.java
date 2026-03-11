package com.codescope.backend.config;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.Optional;

@Component
@ConditionalOnBean(MeterRegistry.class)
@Order(Ordered.HIGHEST_PRECEDENCE + 50)
public class ApiMetricsWebFilter implements WebFilter {

    private static final String LATENCY_METRIC = "codescope.http.server.latency";
    private static final String ERROR_METRIC = "codescope.http.server.errors";

    private final MeterRegistry meterRegistry;

    public ApiMetricsWebFilter(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String path = exchange.getRequest().getPath().value();
        String method = exchange.getRequest().getMethod() != null ? exchange.getRequest().getMethod().name() : "UNKNOWN";

        Optional<String> group = resolveApiGroup(path);
        if (group.isEmpty()) {
            return chain.filter(exchange);
        }

        Timer.Sample sample = Timer.start(meterRegistry);
        String apiGroup = group.get();

        return chain.filter(exchange)
                .doOnError(throwable -> Counter.builder(ERROR_METRIC)
                        .tag("api_group", apiGroup)
                        .tag("method", method)
                        .tag("exception", throwable.getClass().getSimpleName())
                        .register(meterRegistry)
                        .increment())
                .doFinally(signalType -> {
                    HttpStatusCode status = exchange.getResponse().getStatusCode();
                    int statusCode = status != null ? status.value() : 500;
                    String statusClass = (statusCode / 100) + "xx";

                    sample.stop(Timer.builder(LATENCY_METRIC)
                            .description("CodeScope endpoint latency for baseline profiling")
                            .tag("api_group", apiGroup)
                            .tag("method", method)
                            .tag("status", String.valueOf(statusCode))
                            .tag("status_class", statusClass)
                            .register(meterRegistry));
                });
    }

    private Optional<String> resolveApiGroup(String path) {
        if (path == null || !path.startsWith("/api/")) {
            return Optional.empty();
        }
        if (path.startsWith("/api/auth")) {
            return Optional.of("auth");
        }
        if (path.startsWith("/api/upload")) {
            return Optional.of("upload");
        }
        if (path.startsWith("/api/projects")) {
            return Optional.of("projects");
        }
        if (path.startsWith("/api/job")) {
            return Optional.of("jobs");
        }
        if (path.startsWith("/api/chat")) {
            return Optional.of("chat");
        }
        if (path.startsWith("/api/reports")) {
            return Optional.of("reports");
        }
        if (path.startsWith("/api/dashboard")) {
            return Optional.of("dashboard");
        }
        if (path.startsWith("/api/notifications")) {
            return Optional.of("notifications");
        }
        if (path.startsWith("/api/export")) {
            return Optional.of("export");
        }
        return Optional.of("other");
    }
}
