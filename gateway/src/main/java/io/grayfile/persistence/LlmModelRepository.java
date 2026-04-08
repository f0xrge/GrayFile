package io.grayfile.persistence;

import io.grayfile.domain.LlmModelEntity;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class LlmModelRepository implements PanacheRepositoryBase<LlmModelEntity, String> {
}
