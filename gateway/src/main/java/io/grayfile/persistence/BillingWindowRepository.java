package io.grayfile.persistence;

import io.grayfile.domain.BillingWindowEntity;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import io.quarkus.panache.common.Parameters;
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

    public List<BillingWindowEntity> listFiltered(String customerId, String apiKeyId, Instant startFrom, Instant endTo) {
        StringBuilder query = new StringBuilder("1 = 1");
        Parameters parameters = new Parameters();

        if (customerId != null) {
            query.append(" and customerId = :customerId");
            parameters.and("customerId", customerId);
        }
        if (apiKeyId != null) {
            query.append(" and apiKeyId = :apiKeyId");
            parameters.and("apiKeyId", apiKeyId);
        }
        if (startFrom != null) {
            query.append(" and (windowEnd is null or windowEnd >= :startFrom)");
            parameters.and("startFrom", startFrom);
        }
        if (endTo != null) {
            query.append(" and windowStart <= :endTo");
            parameters.and("endTo", endTo);
        }

        query.append(" order by windowStart desc, id desc");
        return find(query.toString(), parameters).list();
    }
}
