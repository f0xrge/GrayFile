package io.grayfile.domain;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

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
}
