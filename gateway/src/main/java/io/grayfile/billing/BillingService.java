package io.grayfile.billing;

import io.grayfile.domain.BillingWindowEntity;
import io.grayfile.domain.UsageEventEntity;
import io.grayfile.metrics.BillingMetrics;
import io.grayfile.persistence.BillingWindowRepository;
import io.grayfile.persistence.UsageEventRepository;
import io.grayfile.service.AuditLogService;
import io.grayfile.service.PricingService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class BillingService implements BillingUsageHandler {

    public static final int TOKEN_LIMIT = 1000;
    public static final Duration TIME_LIMIT = Duration.ofMinutes(10);

    private final UsageEventRepository usageEventRepository;
    private final BillingWindowRepository billingWindowRepository;
    private final BillingMetrics billingMetrics;
    private final AuditLogService auditLogService;
    private final PricingService pricingService;

    public BillingService(UsageEventRepository usageEventRepository,
                          BillingWindowRepository billingWindowRepository,
                          BillingMetrics billingMetrics,
                          AuditLogService auditLogService,
                          PricingService pricingService) {
        this.usageEventRepository = usageEventRepository;
        this.billingWindowRepository = billingWindowRepository;
        this.billingMetrics = billingMetrics;
        this.auditLogService = auditLogService;
        this.pricingService = pricingService;
    }

    @Override
    @Transactional
    public void handleUsage(String customerId,
                            String apiKeyId,
                            String model,
                            String requestId,
                            long durationMs,
                            int promptTokens,
                            int completionTokens,
                            int totalTokens,
                            String contractVersion,
                            String extractorVersion,
                            String usageSignature,
                            Instant eventTime) {
        Optional<UsageEventEntity> existing = usageEventRepository.findByBusinessKey(requestId, customerId, apiKeyId, model);
        if (existing.isPresent()) {
            logDeduplicated(existing.get(), requestId, customerId, apiKeyId, model, eventTime);
            return;
        }

        UsageEventEntity usageEvent = new UsageEventEntity();
        usageEvent.id = UUID.randomUUID();
        usageEvent.customerId = customerId;
        usageEvent.apiKeyId = apiKeyId;
        usageEvent.model = model;
        usageEvent.requestId = requestId;
        usageEvent.durationMs = Math.max(durationMs, 0);
        usageEvent.promptTokens = promptTokens;
        usageEvent.completionTokens = completionTokens;
        usageEvent.totalTokens = totalTokens;
        usageEvent.contractVersion = contractVersion;
        usageEvent.extractorVersion = extractorVersion;
        usageEvent.usageSignature = usageSignature;
        PricingService.EffectivePricing pricing = pricingService.resolveEffectivePricing(customerId, model);
        PricingService.CostBreakdown costBreakdown = pricingService.calculateCost(pricing, usageEvent.durationMs, totalTokens);
        usageEvent.billedTimePrice = pricing.timePricePerSecond();
        usageEvent.billedTokenPrice = pricing.tokenPricePerThousandTokens();
        usageEvent.timeCost = costBreakdown.timeCost();
        usageEvent.tokenCost = costBreakdown.tokenCost();
        usageEvent.totalCost = costBreakdown.totalCost();
        usageEvent.pricingSource = pricing.source();
        usageEvent.eventTime = eventTime;
        try {
            usageEventRepository.persistAndFlush(usageEvent);
        } catch (RuntimeException exception) {
            Optional<UsageEventEntity> duplicated = usageEventRepository.findByBusinessKey(requestId, customerId, apiKeyId, model);
            if (duplicated.isPresent()) {
                logDeduplicated(duplicated.get(), requestId, customerId, apiKeyId, model, eventTime);
                return;
            }
            throw exception;
        }

        billingMetrics.recordUsageEvent(totalTokens);
        auditLogService.logEvent(
                "BILLING_USAGE_INGESTED",
                "billing-service",
                "usage_event",
                usageEvent.id.toString(),
                auditLogService.payloadOf(
                        "request_id", requestId,
                        "customer_id", customerId,
                        "api_key_id", apiKeyId,
                        "model", model,
                        "duration_ms", usageEvent.durationMs,
                        "prompt_tokens", promptTokens,
                        "completion_tokens", completionTokens,
                        "total_tokens", totalTokens,
                        "contract_version", contractVersion,
                        "extractor_version", extractorVersion,
                        "usage_signature", usageSignature,
                        "billed_time_price", usageEvent.billedTimePrice,
                        "billed_token_price", usageEvent.billedTokenPrice,
                        "time_cost", usageEvent.timeCost,
                        "token_cost", usageEvent.tokenCost,
                        "total_cost", usageEvent.totalCost,
                        "pricing_source", usageEvent.pricingSource
                ),
                eventTime
        );

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
        auditLogService.logEvent(
                "BILLING_WINDOW_OPENED",
                "billing-service",
                "billing_window",
                entity.id.toString(),
                auditLogService.payloadOf(
                        "customer_id", customerId,
                        "api_key_id", apiKeyId,
                        "model", model,
                        "window_start", startTime
                ),
                startTime
        );
        return entity;
    }

    private void closeWindow(BillingWindowEntity window, Instant closeAt, String reason) {
        window.windowEnd = closeAt;
        window.closureReason = reason;
        window.active = false;
        billingMetrics.recordWindowClose();
        auditLogService.logEvent(
                "BILLING_WINDOW_CLOSED",
                "billing-service",
                "billing_window",
                window.id.toString(),
                auditLogService.payloadOf(
                        "customer_id", window.customerId,
                        "api_key_id", window.apiKeyId,
                        "model", window.model,
                        "window_start", window.windowStart,
                        "window_end", closeAt,
                        "token_total", window.tokenTotal,
                        "closure_reason", reason
                ),
                closeAt
        );
    }

    private void logDeduplicated(UsageEventEntity existing,
                                 String requestId,
                                 String customerId,
                                 String apiKeyId,
                                 String model,
                                 Instant occurredAt) {
        auditLogService.logEvent(
                "BILLING_USAGE_DEDUPLICATED",
                "billing-service",
                "usage_event",
                existing.id.toString(),
                auditLogService.payloadOf(
                        "request_id", requestId,
                        "customer_id", customerId,
                        "api_key_id", apiKeyId,
                        "model", model
                ),
                occurredAt
        );
    }
}
