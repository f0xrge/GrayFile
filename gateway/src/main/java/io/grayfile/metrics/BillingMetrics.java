package io.grayfile.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class BillingMetrics {

    private final Counter usageEvents;
    private final Counter billingWindowClosed;
    private final Counter billableTokens;

    public BillingMetrics(MeterRegistry meterRegistry) {
        this.usageEvents = meterRegistry.counter("grayfile_usage_events_total");
        this.billingWindowClosed = meterRegistry.counter("grayfile_billing_windows_closed_total");
        this.billableTokens = meterRegistry.counter("grayfile_billable_tokens_total");
    }

    public void recordUsageEvent(int totalTokens) {
        usageEvents.increment();
        billableTokens.increment(totalTokens);
    }

    public void recordWindowClose() {
        billingWindowClosed.increment();
    }
}
