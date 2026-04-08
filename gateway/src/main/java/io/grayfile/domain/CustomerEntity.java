package io.grayfile.domain;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "customers")
public class CustomerEntity extends PanacheEntityBase {

    @Id
    public String id;

    @Column(nullable = false)
    public String name;

    @Column(nullable = false)
    public boolean active;
}
