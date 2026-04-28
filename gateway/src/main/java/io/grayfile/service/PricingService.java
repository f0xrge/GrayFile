package io.grayfile.service;

import io.grayfile.domain.CustomerModelPricingEntity;
import io.grayfile.domain.LlmModelEntity;
import io.grayfile.persistence.CustomerModelPricingRepository;
import io.grayfile.persistence.LlmModelRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.NotFoundException;

import java.math.BigDecimal;
import java.math.RoundingMode;

@ApplicationScoped
public class PricingService {

    public static final int DEFAULT_TIME_CRITERION_SECONDS = 1;
    public static final int DEFAULT_TOKEN_CRITERION = 1000;

    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(6, RoundingMode.HALF_UP);
    private static final BigDecimal MILLIS_PER_SECOND = BigDecimal.valueOf(1000);

    private final LlmModelRepository llmModelRepository;
    private final CustomerModelPricingRepository customerModelPricingRepository;

    public PricingService(LlmModelRepository llmModelRepository,
                          CustomerModelPricingRepository customerModelPricingRepository) {
        this.llmModelRepository = llmModelRepository;
        this.customerModelPricingRepository = customerModelPricingRepository;
    }

    public EffectivePricing resolveEffectivePricing(String customerId, String modelId) {
        CustomerModelPricingEntity override = customerModelPricingRepository.findByCustomerAndModel(customerId, modelId).orElse(null);
        if (override != null) {
            return new EffectivePricing(
                    normalizeCriterion(override.timeCriterionSeconds, "customer-model time criterion"),
                    normalizePrice(override.timePrice, "customer-model time price"),
                    normalizeCriterion(override.tokenCriterion, "customer-model token criterion"),
                    normalizePrice(override.tokenPrice, "customer-model token price"),
                    "customer-model"
            );
        }

        LlmModelEntity model = llmModelRepository.findByIdOptional(modelId)
                .orElseThrow(() -> new NotFoundException("model not found: " + modelId));
        return new EffectivePricing(
                normalizeDefaultCriterion(model.defaultTimeCriterionSeconds, DEFAULT_TIME_CRITERION_SECONDS),
                normalizeNullablePrice(model.defaultTimePrice),
                normalizeDefaultCriterion(model.defaultTokenCriterion, DEFAULT_TOKEN_CRITERION),
                normalizeNullablePrice(model.defaultTokenPrice),
                "model-default"
        );
    }

    public CostBreakdown calculateCost(EffectivePricing pricing, long durationMs, int totalTokens) {
        return calculateCost(pricing, durationMs, "tokens", BigDecimal.valueOf(totalTokens));
    }

    public CostBreakdown calculateCost(EffectivePricing pricing,
                                       long durationMs,
                                       String billableUnitType,
                                       BigDecimal billableUnitCount) {
        BigDecimal durationSeconds = BigDecimal.valueOf(Math.max(durationMs, 0))
                .divide(MILLIS_PER_SECOND, 6, RoundingMode.HALF_UP);
        BigDecimal normalizedUnits = billableUnitCount == null ? ZERO : billableUnitCount.max(BigDecimal.ZERO).setScale(6, RoundingMode.HALF_UP);

        BigDecimal timeCost = pricing.timePrice()
                .multiply(durationSeconds)
                .divide(BigDecimal.valueOf(pricing.timeCriterionSeconds()), 6, RoundingMode.HALF_UP);
        BigDecimal tokenCost = pricing.tokenPrice()
                .multiply(normalizedUnits)
                .divide(BigDecimal.valueOf(pricing.tokenCriterion()), 6, RoundingMode.HALF_UP);
        BigDecimal totalCost = timeCost.add(tokenCost).setScale(6, RoundingMode.HALF_UP);

        return new CostBreakdown(
                timeCost.setScale(6, RoundingMode.HALF_UP),
                tokenCost.setScale(6, RoundingMode.HALF_UP),
                totalCost
        );
    }

    public int normalizeCriterion(Integer value, String label) {
        if (value == null) {
            throw new IllegalArgumentException(label + " is required");
        }
        if (value <= 0) {
            throw new IllegalArgumentException(label + " must be > 0");
        }
        return value;
    }

    public int normalizeDefaultCriterion(Integer value, int defaultValue) {
        if (value == null || value <= 0) {
            return defaultValue;
        }
        return value;
    }

    public BigDecimal normalizePrice(BigDecimal value, String label) {
        if (value == null) {
            throw new IllegalArgumentException(label + " is required");
        }
        if (value.signum() < 0) {
            throw new IllegalArgumentException(label + " must be >= 0");
        }
        return value.setScale(6, RoundingMode.HALF_UP);
    }

    public BigDecimal normalizeNullablePrice(BigDecimal value) {
        return value == null ? ZERO : normalizePrice(value, "price");
    }

    public record EffectivePricing(int timeCriterionSeconds,
                                   BigDecimal timePrice,
                                   int tokenCriterion,
                                   BigDecimal tokenPrice,
                                   String source) {
    }

    public record CostBreakdown(BigDecimal timeCost, BigDecimal tokenCost, BigDecimal totalCost) {
    }
}
