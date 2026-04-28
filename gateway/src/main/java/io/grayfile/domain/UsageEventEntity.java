package io.grayfile.domain;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "usage_events")
public class UsageEventEntity extends PanacheEntityBase {

    @Id
    public UUID id;

    @Column(name = "request_id", nullable = false)
    public String requestId;

    @Column(name = "customer_id", nullable = false)
    public String customerId;

    @Column(name = "api_key_id", nullable = false)
    public String apiKeyId;

    @Column(name = "model_name", nullable = false)
    public String model;

    @Column(name = "event_time", nullable = false)
    public Instant eventTime;

    @Column(name = "prompt_tokens", nullable = false)
    public int promptTokens;

    @Column(name = "completion_tokens", nullable = false)
    public int completionTokens;

    @Column(name = "input_tokens")
    public Integer inputTokens;

    @Column(name = "output_tokens")
    public Integer outputTokens;

    @Column(name = "total_tokens", nullable = false)
    public int totalTokens;

    @Column(name = "endpoint_type", nullable = false)
    public String endpointType;

    @Column(name = "billable_unit_type", nullable = false)
    public String billableUnitType;

    @Column(name = "billable_unit_count", nullable = false)
    public BigDecimal billableUnitCount;

    @Column(name = "usage_raw")
    public String usageRaw;

    @Column(name = "duration_ms", nullable = false)
    public long durationMs;

    @Column(name = "contract_version", nullable = false)
    public String contractVersion;

    @Column(name = "extractor_version", nullable = false)
    public String extractorVersion;

    @Column(name = "usage_signature", nullable = false)
    public String usageSignature;

    @Column(name = "billed_time_criterion_seconds", nullable = false)
    public int billedTimeCriterionSeconds;

    @Column(name = "billed_time_price", nullable = false)
    public BigDecimal billedTimePrice;

    @Column(name = "billed_token_criterion", nullable = false)
    public int billedTokenCriterion;

    @Column(name = "billed_token_price", nullable = false)
    public BigDecimal billedTokenPrice;

    @Column(name = "time_cost", nullable = false)
    public BigDecimal timeCost;

    @Column(name = "token_cost", nullable = false)
    public BigDecimal tokenCost;

    @Column(name = "total_cost", nullable = false)
    public BigDecimal totalCost;

    @Column(name = "pricing_source", nullable = false)
    public String pricingSource;
}
