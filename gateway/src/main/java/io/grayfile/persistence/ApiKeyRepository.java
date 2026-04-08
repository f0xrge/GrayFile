package io.grayfile.persistence;

import io.grayfile.domain.ApiKeyEntity;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class ApiKeyRepository implements PanacheRepositoryBase<ApiKeyEntity, String> {

    public List<ApiKeyEntity> listAllOrdered() {
        return list("order by customerId asc, id asc");
    }

    public Optional<ApiKeyEntity> findByIdOptional(String id) {
        return find("id", id).firstResultOptional();
    }
}
