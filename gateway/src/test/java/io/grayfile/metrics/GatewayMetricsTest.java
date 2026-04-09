package io.grayfile.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GatewayMetricsTest {

    @Test
    void shouldRecordRequestLatencyWithExpectedTags() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        GatewayMetrics metrics = new GatewayMetrics(registry);

        metrics.recordRequestLatency("gpt-4o-mini", "customer-1", "key-1", 200, 50_000_000L);

        Timer timer = registry.find("grayfile_gateway_request_latency")
                .tags("model", "gpt-4o-mini", "customer_id", "customer-1", "api_key_id", "key-1", "backend_status", "200")
                .timer();
        assertEquals(1, timer.count());
    }

    @Test
    void shouldRecordApplicationAndBackendErrorCountersFor502503504AndTimeout() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        GatewayMetrics metrics = new GatewayMetrics(registry);

        metrics.recordApplicationError("backend_status", "gpt-4o-mini", "502");
        metrics.recordApplicationError("backend_status", "gpt-4o-mini", "503");
        metrics.recordApplicationError("backend_status", "gpt-4o-mini", "504");
        metrics.recordEdgeError("timeout", "gpt-4o-mini", "504");

        Counter app502 = registry.find("grayfile_application_errors_total")
                .tags("type", "backend_status", "model", "gpt-4o-mini", "backend_status", "502")
                .counter();
        Counter app503 = registry.find("grayfile_application_errors_total")
                .tags("type", "backend_status", "model", "gpt-4o-mini", "backend_status", "503")
                .counter();
        Counter app504 = registry.find("grayfile_application_errors_total")
                .tags("type", "backend_status", "model", "gpt-4o-mini", "backend_status", "504")
                .counter();
        Counter timeout = registry.find("grayfile_edge_errors_total")
                .tags("type", "timeout", "model", "gpt-4o-mini", "backend_status", "504")
                .counter();

        assertEquals(1.0, app502.count());
        assertEquals(1.0, app503.count());
        assertEquals(1.0, app504.count());
        assertEquals(1.0, timeout.count());
    }
}
