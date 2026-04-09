package io.grayfile.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.concurrent.TimeUnit;

@ApplicationScoped
public class GatewayMetrics {

    private static final String EDGE_ERRORS_METRIC = "grayfile_edge_errors_total";
    private static final String APPLICATION_ERRORS_METRIC = "grayfile_application_errors_total";
    private static final String REQUEST_LATENCY_METRIC = "grayfile_gateway_request_latency";

    private final MeterRegistry meterRegistry;

    public GatewayMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public void recordRequestLatency(String model,
                                     String customerId,
                                     String apiKeyId,
                                     int backendStatus,
                                     long latencyNanos) {
        Timer.builder(REQUEST_LATENCY_METRIC)
                .description("Gateway latency observed for LLM proxy calls")
                .tag("model", model)
                .tag("customer_id", customerId)
                .tag("api_key_id", apiKeyId)
                .tag("backend_status", String.valueOf(backendStatus))
                .register(meterRegistry)
                .record(latencyNanos, TimeUnit.NANOSECONDS);
    }

    public void recordEdgeError(String type, String model, String backendStatus) {
        counter(EDGE_ERRORS_METRIC, type, model, backendStatus).increment();
    }

    public void recordApplicationError(String type, String model, String backendStatus) {
        counter(APPLICATION_ERRORS_METRIC, type, model, backendStatus).increment();
    }

    private Counter counter(String metricName, String type, String model, String backendStatus) {
        return Counter.builder(metricName)
                .description("Gateway error counters split by origin")
                .tag("type", type)
                .tag("model", model)
                .tag("backend_status", backendStatus)
                .register(meterRegistry);
    }
}
