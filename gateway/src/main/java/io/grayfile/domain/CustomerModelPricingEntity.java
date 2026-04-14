package io.grayfile.domain;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "customer_model_pricing",
        uniqueConstraints = @UniqueConstraint(name = "ux_customer_model_pricing_scope", columnNames = {"customer_id", "model_id"}))
public class CustomerModelPricingEntity extends PanacheEntityBase {

    @Id
    public UUID id;

    @Column(name = "customer_id", nullable = false)
    public String customerId;

    @Column(name = "model_id", nullable = false)
    public String modelId;

    @Column(name = "time_price", nullable = false)
    public BigDecimal timePrice;

    @Column(name = "token_price", nullable = false)
    public BigDecimal tokenPrice;

    @Column(name = "updated_at", nullable = false)
    public Instant updatedAt;
}
