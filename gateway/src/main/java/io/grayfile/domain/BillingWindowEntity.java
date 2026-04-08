package io.grayfile.domain;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "billing_windows")
public class BillingWindowEntity extends PanacheEntityBase {

    @Id
    public UUID id;

    @Column(name = "customer_id", nullable = false)
    public String customerId;

    @Column(name = "api_key_id", nullable = false)
    public String apiKeyId;

    @Column(name = "model_name", nullable = false)
    public String model;

    @Column(name = "window_start", nullable = false)
    public Instant windowStart;

    @Column(name = "window_end")
    public Instant windowEnd;

    @Column(name = "token_total", nullable = false)
    public int tokenTotal;

    @Column(name = "closure_reason")
    public String closureReason;

    @Column(name = "active", nullable = false)
    public boolean active;
}
