package io.grayfile.persistence;

import io.grayfile.domain.AuditLogEntity;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

import java.time.Instant;
import java.util.ArrayList;
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

    public List<AuditLogEntity> listFiltered(String eventType,
                                             Instant startDate,
                                             Instant endDate,
                                             String entityType,
                                             String entityId,
                                             int limit) {
        StringBuilder query = new StringBuilder("1=1");
        List<Object> params = new ArrayList<>();

        if (eventType != null) {
            query.append(" and eventType = ?").append(params.size() + 1);
            params.add(eventType);
        }
        if (startDate != null) {
            query.append(" and occurredAt >= ?").append(params.size() + 1);
            params.add(startDate);
        }
        if (endDate != null) {
            query.append(" and occurredAt <= ?").append(params.size() + 1);
            params.add(endDate);
        }
        if (entityType != null) {
            query.append(" and entityType = ?").append(params.size() + 1);
            params.add(entityType);
        }
        if (entityId != null) {
            query.append(" and entityId = ?").append(params.size() + 1);
            params.add(entityId);
        }

        return find(query.append(" order by occurredAt desc, eventId desc").toString(), params.toArray())
                .page(0, limit)
                .list();
    }
}
