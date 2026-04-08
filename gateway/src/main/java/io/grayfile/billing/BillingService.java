package io.grayfile.billing;

import io.grayfile.domain.BillingWindowEntity;
import io.grayfile.domain.UsageEventEntity;
import io.grayfile.metrics.BillingMetrics;
import io.grayfile.persistence.BillingWindowRepository;
import io.grayfile.persistence.UsageEventRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class BillingService {

    public static final int TOKEN_LIMIT = 1000;
    public static final Duration TIME_LIMIT = Duration.ofMinutes(10);

    private final UsageEventRepository usageEventRepository;
    private final BillingWindowRepository billingWindowRepository;
    private final BillingMetrics billingMetrics;

    public BillingService(UsageEventRepository usageEventRepository,
                          BillingWindowRepository billingWindowRepository,
                          BillingMetrics billingMetrics) {
        this.usageEventRepository = usageEventRepository;
        this.billingWindowRepository = billingWindowRepository;
        this.billingMetrics = billingMetrics;
    }

    @Transactional
    public void handleUsage(String customerId,
                            String apiKeyId,
                            String model,
                            String requestId,
                            int promptTokens,
                            int completionTokens,
                            int totalTokens,
                            Instant eventTime) {
        UsageEventEntity usageEvent = new UsageEventEntity();
        usageEvent.id = UUID.randomUUID();
        usageEvent.customerId = customerId;
        usageEvent.apiKeyId = apiKeyId;
        usageEvent.model = model;
        usageEvent.requestId = requestId;
        usageEvent.promptTokens = promptTokens;
        usageEvent.completionTokens = completionTokens;
        usageEvent.totalTokens = totalTokens;
        usageEvent.eventTime = eventTime;
        usageEventRepository.persist(usageEvent);
        billingMetrics.recordUsageEvent(totalTokens);

        int remaining = totalTokens;
        while (remaining > 0) {
            BillingWindowEntity window = getOrCreateActiveWindow(customerId, apiKeyId, model, eventTime);
            int available = TOKEN_LIMIT - window.tokenTotal;
            int consumed = Math.min(available, remaining);
            window.tokenTotal += consumed;
            remaining -= consumed;

            if (window.tokenTotal >= TOKEN_LIMIT) {
                closeWindow(window, eventTime, "TOKEN_LIMIT");
            }
        }
    }

    @Transactional
    public void closeExpiredWindows(Instant now) {
        Instant cutoff = now.minus(TIME_LIMIT);
        for (BillingWindowEntity window : billingWindowRepository.findExpiredActive(cutoff)) {
            closeWindow(window, window.windowStart.plus(TIME_LIMIT), "TIME_LIMIT");
        }
    }

    private BillingWindowEntity getOrCreateActiveWindow(String customerId, String apiKeyId, String model, Instant startTime) {
        Optional<BillingWindowEntity> active = billingWindowRepository.findActive(customerId, apiKeyId, model);
        if (active.isPresent()) {
            return active.get();
        }

        BillingWindowEntity entity = new BillingWindowEntity();
        entity.id = UUID.randomUUID();
        entity.customerId = customerId;
        entity.apiKeyId = apiKeyId;
        entity.model = model;
        entity.windowStart = startTime;
        entity.tokenTotal = 0;
        entity.active = true;
        billingWindowRepository.persist(entity);
        return entity;
    }

    private void closeWindow(BillingWindowEntity window, Instant closeAt, String reason) {
        window.windowEnd = closeAt;
        window.closureReason = reason;
        window.active = false;
        billingMetrics.recordWindowClose();
    }
}
