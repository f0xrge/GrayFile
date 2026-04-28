package io.grayfile.persistence;

import io.grayfile.domain.BillingWindowEntity;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.UserTransaction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

@QuarkusTest
class BillingWindowRepositoryTest {

    @Inject
    BillingWindowRepository billingWindowRepository;

    @Inject
    UserTransaction userTransaction;

    @BeforeEach
    void clean() throws Exception {
        userTransaction.begin();
        try {
            billingWindowRepository.deleteAll();
            userTransaction.commit();
        } catch (Exception exception) {
            userTransaction.rollback();
            throw exception;
        }
    }

    @Test
    void shouldFindExpiredActiveWindowsOnly() {
        persistWindow("customer-1", "key-1", "gpt-4o-mini", Instant.parse("2026-04-09T08:00:00Z"), true);
        persistWindow("customer-1", "key-1", "gpt-4o-mini", Instant.parse("2026-04-09T11:59:59Z"), true);
        persistWindow("customer-1", "key-1", "gpt-4o-mini", Instant.parse("2026-04-09T07:00:00Z"), false);

        List<BillingWindowEntity> result = billingWindowRepository.findExpiredActive(Instant.parse("2026-04-09T09:00:00Z"));

        assertEquals(1, result.size());
        assertEquals(Instant.parse("2026-04-09T08:00:00Z"), result.getFirst().windowStart);
    }

    @Test
    void shouldFilterBillingWindowsByScopeAndDatesInDescendingOrder() {
        persistWindow("customer-1", "key-1", "gpt-4o-mini", Instant.parse("2026-04-09T08:00:00Z"), false);
        persistWindow("customer-1", "key-2", "gpt-4o-mini", Instant.parse("2026-04-09T09:00:00Z"), true);
        persistWindow("customer-2", "key-3", "gpt-4o-mini", Instant.parse("2026-04-09T10:00:00Z"), false);

        List<BillingWindowEntity> customerOnly = billingWindowRepository.listFiltered("customer-1", null, null, null);
        assertEquals(2, customerOnly.size());
        assertEquals("key-2", customerOnly.get(0).apiKeyId);
        assertEquals("key-1", customerOnly.get(1).apiKeyId);

        List<BillingWindowEntity> withDates = billingWindowRepository.listFiltered(
                null,
                null,
                Instant.parse("2026-04-09T09:30:00Z"),
                Instant.parse("2026-04-09T11:00:00Z")
        );
        assertEquals(2, withDates.size());
        assertEquals("customer-2", withDates.get(0).customerId);
        assertEquals("customer-1", withDates.get(1).customerId);
    }

    private void persistWindow(String customerId, String apiKeyId, String model, Instant start, boolean active) {
        try {
            userTransaction.begin();
            BillingWindowEntity entity = new BillingWindowEntity();
            entity.id = UUID.randomUUID();
            entity.customerId = customerId;
            entity.apiKeyId = apiKeyId;
            entity.model = model;
            entity.windowStart = start;
            entity.windowEnd = active ? null : start.plusSeconds(60);
            entity.tokenTotal = 100;
            entity.closureReason = active ? null : "TOKEN_LIMIT";
            entity.active = active;
            billingWindowRepository.persist(entity);
            userTransaction.commit();
        } catch (Exception exception) {
            try {
                userTransaction.rollback();
            } catch (Exception ignored) {
                // Preserve the original exception below.
            }
            throw new RuntimeException(exception);
        }
    }
}
