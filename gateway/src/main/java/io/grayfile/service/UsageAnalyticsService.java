package io.grayfile.service;

import io.grayfile.persistence.UsageEventRepository;
import jakarta.enterprise.context.ApplicationScoped;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@ApplicationScoped
public class UsageAnalyticsService {

    private final UsageEventRepository usageEventRepository;

    public UsageAnalyticsService(UsageEventRepository usageEventRepository) {
        this.usageEventRepository = usageEventRepository;
    }

    public UsageAnalyticsResponse buildAnalytics(String customerId,
                                                 String modelId,
                                                 Instant startFrom,
                                                 Instant endTo,
                                                 int limit) {
        int normalizedLimit = Math.max(1, Math.min(limit, 100));
        UsageEventRepository.UsageAggregate summary = usageEventRepository.summarize(customerId, modelId, startFrom, endTo);
        return new UsageAnalyticsResponse(
                toSummary(summary),
                usageEventRepository.aggregateByCustomer(customerId, modelId, startFrom, endTo, normalizedLimit).stream().map(this::toBreakdown).toList(),
                usageEventRepository.aggregateByModel(customerId, modelId, startFrom, endTo, normalizedLimit).stream().map(this::toBreakdown).toList(),
                usageEventRepository.aggregateByCustomerAndModel(customerId, modelId, startFrom, endTo, normalizedLimit).stream().map(this::toBreakdown).toList(),
                usageEventRepository.aggregateByEndpointAndUnit(customerId, modelId, startFrom, endTo, normalizedLimit).stream().map(this::toBreakdown).toList()
        );
    }

    private UsageSummary toSummary(UsageEventRepository.UsageAggregate aggregate) {
        return new UsageSummary(
                aggregate.requestCount(),
                aggregate.durationMs(),
                aggregate.promptTokens(),
                aggregate.completionTokens(),
                aggregate.totalTokens(),
                aggregate.billableUnitCount(),
                aggregate.timeCost(),
                aggregate.tokenCost(),
                aggregate.totalCost()
        );
    }

    private UsageBreakdown toBreakdown(UsageEventRepository.UsageAggregate aggregate) {
        return new UsageBreakdown(
                aggregate.customerId(),
                aggregate.modelId(),
                aggregate.endpointType(),
                aggregate.billableUnitType(),
                aggregate.requestCount(),
                aggregate.durationMs(),
                aggregate.promptTokens(),
                aggregate.completionTokens(),
                aggregate.totalTokens(),
                aggregate.billableUnitCount(),
                aggregate.timeCost(),
                aggregate.tokenCost(),
                aggregate.totalCost()
        );
    }

    public record UsageAnalyticsResponse(UsageSummary summary,
                                         List<UsageBreakdown> byCustomer,
                                         List<UsageBreakdown> byModel,
                                         List<UsageBreakdown> byCustomerModel,
                                         List<UsageBreakdown> byEndpointUnit) {
    }

    public record UsageSummary(long requestCount,
                               long durationMs,
                               long promptTokens,
                               long completionTokens,
                               long totalTokens,
                               BigDecimal billableUnitCount,
                               BigDecimal timeCost,
                               BigDecimal tokenCost,
                               BigDecimal totalCost) {
    }

    public record UsageBreakdown(String customerId,
                                 String modelId,
                                 String endpointType,
                                 String billableUnitType,
                                 long requestCount,
                                 long durationMs,
                                 long promptTokens,
                                 long completionTokens,
                                 long totalTokens,
                                 BigDecimal billableUnitCount,
                                 BigDecimal timeCost,
                                 BigDecimal tokenCost,
                                 BigDecimal totalCost) {
    }
}
