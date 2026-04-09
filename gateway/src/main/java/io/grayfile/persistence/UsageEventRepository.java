package io.grayfile.persistence;

import io.grayfile.domain.UsageEventEntity;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class UsageEventRepository implements PanacheRepositoryBase<UsageEventEntity, UUID> {

    public Optional<UsageEventEntity> findByBusinessKey(String requestId, String customerId, String apiKeyId, String model) {
        return find("requestId = ?1 and customerId = ?2 and apiKeyId = ?3 and model = ?4",
                requestId, customerId, apiKeyId, model).firstResultOptional();
    }
}
