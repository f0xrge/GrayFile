package io.grayfile.persistence;

import io.grayfile.domain.LiteLlmResourceEntity;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class LiteLlmResourceRepository implements PanacheRepositoryBase<LiteLlmResourceEntity, UUID> {

    public Optional<LiteLlmResourceEntity> findMapping(String entityType, String entityId, String resourceType) {
        return find("entityType = ?1 and entityId = ?2 and liteLlmResourceType = ?3", entityType, entityId, resourceType)
                .firstResultOptional();
    }

    public List<LiteLlmResourceEntity> listFailedOrPending() {
        return list("lastSyncStatus in (?1, ?2) order by updatedAt desc", "pending", "failed");
    }

    public List<LiteLlmResourceEntity> listAllOrdered() {
        return list("order by updatedAt desc, entityType asc, entityId asc");
    }
}
