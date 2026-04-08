package io.grayfile.domain;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "api_keys")
public class ApiKeyEntity extends PanacheEntityBase {

    @Id
    public String id;

    @Column(name = "customer_id", nullable = false)
    public String customerId;

    @Column(nullable = false)
    public String name;

    @Column(nullable = false)
    public boolean active;
}
