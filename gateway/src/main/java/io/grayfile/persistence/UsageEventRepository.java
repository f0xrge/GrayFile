package io.grayfile.persistence;

import io.grayfile.domain.UsageEventEntity;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class UsageEventRepository implements PanacheRepositoryBase<UsageEventEntity, UUID> {

    private final EntityManager entityManager;

    public UsageEventRepository(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    public Optional<UsageEventEntity> findByBusinessKey(String requestId, String customerId, String apiKeyId, String model) {
        return find("requestId = ?1 and customerId = ?2 and apiKeyId = ?3 and model = ?4",
                requestId, customerId, apiKeyId, model).firstResultOptional();
    }

    public UsageAggregate summarize(String customerId, String modelId, Instant startFrom, Instant endTo) {
        List<Object[]> rows = buildAggregateQuery(
                "null, null, count(u), coalesce(sum(u.durationMs), 0), coalesce(sum(u.promptTokens), 0), coalesce(sum(u.completionTokens), 0), coalesce(sum(u.totalTokens), 0), coalesce(sum(u.timeCost), 0), coalesce(sum(u.tokenCost), 0), coalesce(sum(u.totalCost), 0)",
                null,
                "coalesce(sum(u.totalCost), 0) desc, coalesce(sum(u.totalTokens), 0) desc",
                customerId,
                modelId,
                startFrom,
                endTo,
                1
        );
        if (rows.isEmpty()) {
            return UsageAggregate.empty();
        }
        return toAggregate(rows.get(0));
    }

    public List<UsageAggregate> aggregateByCustomer(String customerId, String modelId, Instant startFrom, Instant endTo, int limit) {
        return buildAggregateQuery(
                "u.customerId, null, count(u), coalesce(sum(u.durationMs), 0), coalesce(sum(u.promptTokens), 0), coalesce(sum(u.completionTokens), 0), coalesce(sum(u.totalTokens), 0), coalesce(sum(u.timeCost), 0), coalesce(sum(u.tokenCost), 0), coalesce(sum(u.totalCost), 0)",
                "u.customerId",
                "coalesce(sum(u.totalCost), 0) desc, coalesce(sum(u.totalTokens), 0) desc, u.customerId asc",
                customerId,
                modelId,
                startFrom,
                endTo,
                limit
        ).stream().map(this::toAggregate).toList();
    }

    public List<UsageAggregate> aggregateByModel(String customerId, String modelId, Instant startFrom, Instant endTo, int limit) {
        return buildAggregateQuery(
                "null, u.model, count(u), coalesce(sum(u.durationMs), 0), coalesce(sum(u.promptTokens), 0), coalesce(sum(u.completionTokens), 0), coalesce(sum(u.totalTokens), 0), coalesce(sum(u.timeCost), 0), coalesce(sum(u.tokenCost), 0), coalesce(sum(u.totalCost), 0)",
                "u.model",
                "coalesce(sum(u.totalCost), 0) desc, coalesce(sum(u.totalTokens), 0) desc, u.model asc",
                customerId,
                modelId,
                startFrom,
                endTo,
                limit
        ).stream().map(this::toAggregate).toList();
    }

    public List<UsageAggregate> aggregateByCustomerAndModel(String customerId, String modelId, Instant startFrom, Instant endTo, int limit) {
        return buildAggregateQuery(
                "u.customerId, u.model, count(u), coalesce(sum(u.durationMs), 0), coalesce(sum(u.promptTokens), 0), coalesce(sum(u.completionTokens), 0), coalesce(sum(u.totalTokens), 0), coalesce(sum(u.timeCost), 0), coalesce(sum(u.tokenCost), 0), coalesce(sum(u.totalCost), 0)",
                "u.customerId, u.model",
                "coalesce(sum(u.totalCost), 0) desc, coalesce(sum(u.totalTokens), 0) desc, u.customerId asc, u.model asc",
                customerId,
                modelId,
                startFrom,
                endTo,
                limit
        ).stream().map(this::toAggregate).toList();
    }

    private List<Object[]> buildAggregateQuery(String selectClause,
                                               String groupByClause,
                                               String orderByClause,
                                               String customerId,
                                               String modelId,
                                               Instant startFrom,
                                               Instant endTo,
                                               int limit) {
        StringBuilder jpql = new StringBuilder("select ");
        jpql.append(selectClause).append(" from UsageEventEntity u where 1 = 1");
        Map<String, Object> parameters = new HashMap<>();

        if (customerId != null) {
            jpql.append(" and u.customerId = :customerId");
            parameters.put("customerId", customerId);
        }
        if (modelId != null) {
            jpql.append(" and u.model = :modelId");
            parameters.put("modelId", modelId);
        }
        if (startFrom != null) {
            jpql.append(" and u.eventTime >= :startFrom");
            parameters.put("startFrom", startFrom);
        }
        if (endTo != null) {
            jpql.append(" and u.eventTime <= :endTo");
            parameters.put("endTo", endTo);
        }
        if (groupByClause != null) {
            jpql.append(" group by ").append(groupByClause);
        }
        jpql.append(" order by ").append(orderByClause);

        var query = entityManager.createQuery(jpql.toString(), Object[].class);
        parameters.forEach(query::setParameter);
        query.setMaxResults(Math.max(limit, 1));
        return query.getResultList();
    }

    private UsageAggregate toAggregate(Object[] row) {
        return new UsageAggregate(
                (String) row[0],
                (String) row[1],
                toLong(row[2]),
                toLong(row[3]),
                toLong(row[4]),
                toLong(row[5]),
                toLong(row[6]),
                toBigDecimal(row[7]),
                toBigDecimal(row[8]),
                toBigDecimal(row[9])
        );
    }

    private long toLong(Object value) {
        return value == null ? 0L : ((Number) value).longValue();
    }

    private BigDecimal toBigDecimal(Object value) {
        if (value == null) {
            return BigDecimal.ZERO.setScale(6);
        }
        if (value instanceof BigDecimal bigDecimal) {
            return bigDecimal.setScale(6);
        }
        return BigDecimal.valueOf(((Number) value).doubleValue()).setScale(6);
    }

    public record UsageAggregate(String customerId,
                                 String modelId,
                                 long requestCount,
                                 long durationMs,
                                 long promptTokens,
                                 long completionTokens,
                                 long totalTokens,
                                 BigDecimal timeCost,
                                 BigDecimal tokenCost,
                                 BigDecimal totalCost) {
        public static UsageAggregate empty() {
            return new UsageAggregate(
                    null,
                    null,
                    0,
                    0,
                    0,
                    0,
                    0,
                    BigDecimal.ZERO.setScale(6),
                    BigDecimal.ZERO.setScale(6),
                    BigDecimal.ZERO.setScale(6)
            );
        }
    }
}
