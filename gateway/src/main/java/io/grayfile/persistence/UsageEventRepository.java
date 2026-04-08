package io.grayfile.persistence;

import io.grayfile.domain.UsageEventEntity;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.UUID;

@ApplicationScoped
public class UsageEventRepository implements PanacheRepositoryBase<UsageEventEntity, UUID> {
}
