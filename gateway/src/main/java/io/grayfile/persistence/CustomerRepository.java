package io.grayfile.persistence;

import io.grayfile.domain.CustomerEntity;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class CustomerRepository implements PanacheRepositoryBase<CustomerEntity, String> {
}
