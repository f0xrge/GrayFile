package io.grayfile.billing;

import io.grayfile.persistence.AuditExportStateRepository;
import io.grayfile.persistence.AuditLogRepository;
import io.grayfile.persistence.BillingWindowRepository;
import io.grayfile.persistence.UsageEventRepository;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.UserTransaction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;

@QuarkusTest
class BillingServiceReplayTest {
    private static final String CONTRACT_VERSION = "usage_extraction.v1";
    private static final String EXTRACTOR_VERSION = "gateway-backend-payload-v1";
    private static final String USAGE_SIGNATURE = "signature";

    @Inject
    BillingService billingService;

    @Inject
    UsageEventRepository usageEventRepository;

    @Inject
    BillingWindowRepository billingWindowRepository;

    @Inject
    AuditLogRepository auditLogRepository;

    @Inject
    AuditExportStateRepository auditExportStateRepository;

    @Inject
    UserTransaction userTransaction;

    @BeforeEach
    void cleanDatabase() throws Exception {
        userTransaction.begin();
        billingWindowRepository.deleteAll();
        usageEventRepository.deleteAll();
        auditLogRepository.deleteAll();
        auditExportStateRepository.deleteAll();
        userTransaction.commit();
    }

    @Test
    void shouldDeduplicateReplayOnSameRequestId() {
        Instant eventTime = Instant.parse("2026-04-09T10:15:30Z");

        billingService.handleUsage("customer-1", "key-1", "gpt-4o-mini", "req-1", 40, 60, 100, CONTRACT_VERSION, EXTRACTOR_VERSION, USAGE_SIGNATURE, eventTime);
        billingService.handleUsage("customer-1", "key-1", "gpt-4o-mini", "req-1", 40, 60, 100, CONTRACT_VERSION, EXTRACTOR_VERSION, USAGE_SIGNATURE, eventTime.plusSeconds(1));

        assertEquals(1L, usageEventRepository.count());
        assertEquals(1L, billingWindowRepository.count());
        assertEquals(100, billingWindowRepository.listAll().getFirst().tokenTotal);
        assertEquals(1L, auditLogRepository.find("eventType", "BILLING_USAGE_DEDUPLICATED").count());
    }

    @Test
    void shouldIgnoreRetryWithChangedTokenPayloadOnSameRequestId() {
        Instant eventTime = Instant.parse("2026-04-09T11:15:30Z");

        billingService.handleUsage("customer-1", "key-1", "gpt-4o-mini", "req-2", 30, 20, 50, CONTRACT_VERSION, EXTRACTOR_VERSION, USAGE_SIGNATURE, eventTime);
        billingService.handleUsage("customer-1", "key-1", "gpt-4o-mini", "req-2", 300, 200, 500, CONTRACT_VERSION, EXTRACTOR_VERSION, USAGE_SIGNATURE, eventTime.plusSeconds(5));

        assertEquals(1L, usageEventRepository.count());
        assertEquals(50, usageEventRepository.listAll().getFirst().totalTokens);
        assertEquals(50, billingWindowRepository.listAll().getFirst().tokenTotal);
        assertEquals(1L, auditLogRepository.find("eventType", "BILLING_USAGE_DEDUPLICATED").count());
    }
}
