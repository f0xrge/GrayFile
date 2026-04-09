package io.grayfile.persistence;

import io.grayfile.domain.ModelRouteEntity;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class ModelRouteRepository implements PanacheRepositoryBase<ModelRouteEntity, ModelRouteEntity.ModelRouteId> {

    public List<ModelRouteEntity> listByModel(String modelId) {
        return list("modelId = ?1 order by weight desc, backendId asc", modelId);
    }

    public List<ModelRouteEntity> listActiveByModel(String modelId) {
        return list("modelId = ?1 and active = true order by weight desc, backendId asc", modelId);
    }

    public Optional<ModelRouteEntity> findByModelAndBackend(String modelId, String backendId) {
        return find("modelId = ?1 and backendId = ?2", modelId, backendId).firstResultOptional();
    }

    public long deleteByModelAndBackend(String modelId, String backendId) {
        return delete("modelId = ?1 and backendId = ?2", modelId, backendId);
    }
}
