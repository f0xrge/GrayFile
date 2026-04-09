package io.grayfile.persistence;

import io.grayfile.domain.AuditLogEntity;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class AuditLogRepository implements PanacheRepositoryBase<AuditLogEntity, Long> {

    public Optional<AuditLogEntity> findLatest() {
        return find("order by eventId desc").firstResultOptional();
    }

    public List<AuditLogEntity> findBatchAfter(long eventId, int batchSize) {
        return find("eventId > ?1 order by eventId asc", eventId)
                .page(0, batchSize)
                .list();
    }
}
