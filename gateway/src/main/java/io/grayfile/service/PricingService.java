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

    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(6, RoundingMode.HALF_UP);
    private static final BigDecimal MILLIS_PER_SECOND = BigDecimal.valueOf(1000);
    private static final BigDecimal TOKENS_PER_UNIT = BigDecimal.valueOf(1000);

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
                    normalizePrice(override.timePrice, "customer-model time price"),
                    normalizePrice(override.tokenPrice, "customer-model token price"),
                    "customer-model"
            );
        }

        LlmModelEntity model = llmModelRepository.findByIdOptional(modelId)
                .orElseThrow(() -> new NotFoundException("model not found: " + modelId));
        return new EffectivePricing(
                normalizeNullablePrice(model.defaultTimePrice),
                normalizeNullablePrice(model.defaultTokenPrice),
                "model-default"
        );
    }

    public CostBreakdown calculateCost(EffectivePricing pricing, long durationMs, int totalTokens) {
        BigDecimal durationSeconds = BigDecimal.valueOf(Math.max(durationMs, 0))
                .divide(MILLIS_PER_SECOND, 6, RoundingMode.HALF_UP);
        BigDecimal tokenUnits = BigDecimal.valueOf(Math.max(totalTokens, 0))
                .divide(TOKENS_PER_UNIT, 6, RoundingMode.HALF_UP);

        BigDecimal timeCost = pricing.timePricePerSecond().multiply(durationSeconds).setScale(6, RoundingMode.HALF_UP);
        BigDecimal tokenCost = pricing.tokenPricePerThousandTokens().multiply(tokenUnits).setScale(6, RoundingMode.HALF_UP);
        BigDecimal totalCost = timeCost.add(tokenCost).setScale(6, RoundingMode.HALF_UP);

        return new CostBreakdown(timeCost, tokenCost, totalCost);
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

    public record EffectivePricing(BigDecimal timePricePerSecond,
                                   BigDecimal tokenPricePerThousandTokens,
                                   String source) {
    }

    public record CostBreakdown(BigDecimal timeCost, BigDecimal tokenCost, BigDecimal totalCost) {
    }
}
