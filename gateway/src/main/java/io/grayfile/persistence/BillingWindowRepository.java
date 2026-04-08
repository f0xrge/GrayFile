package io.grayfile.persistence;

import io.grayfile.domain.BillingWindowEntity;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class BillingWindowRepository implements PanacheRepositoryBase<BillingWindowEntity, UUID> {

    public Optional<BillingWindowEntity> findActive(String customerId, String apiKeyId, String model) {
        return find("customerId = ?1 and apiKeyId = ?2 and model = ?3 and active = true", customerId, apiKeyId, model)
                .firstResultOptional();
    }

    public List<BillingWindowEntity> findExpiredActive(Instant cutoff) {
        return find("active = true and windowStart <= ?1", cutoff).list();
    }
}
