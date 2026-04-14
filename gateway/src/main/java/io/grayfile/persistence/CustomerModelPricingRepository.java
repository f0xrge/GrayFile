package io.grayfile.persistence;

import io.grayfile.domain.CustomerModelPricingEntity;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class CustomerModelPricingRepository implements PanacheRepositoryBase<CustomerModelPricingEntity, UUID> {

    public Optional<CustomerModelPricingEntity> findByCustomerAndModel(String customerId, String modelId) {
        return find("customerId = ?1 and modelId = ?2", customerId, modelId).firstResultOptional();
    }

    public List<CustomerModelPricingEntity> listByModel(String modelId) {
        return find("modelId = ?1 order by customerId asc", modelId).list();
    }
}
