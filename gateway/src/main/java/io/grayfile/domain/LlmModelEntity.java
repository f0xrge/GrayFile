package io.grayfile.domain;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;

@Entity
@Table(name = "llm_models")
public class LlmModelEntity extends PanacheEntityBase {

    @Id
    public String id;

    @Column(name = "display_name", nullable = false)
    public String displayName;

    @Column(nullable = false)
    public String provider;

    @Column(nullable = false)
    public boolean active;

    @Column(name = "default_time_price", nullable = false)
    public BigDecimal defaultTimePrice;

    @Column(name = "default_token_price", nullable = false)
    public BigDecimal defaultTokenPrice;
}
