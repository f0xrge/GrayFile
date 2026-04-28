package io.grayfile.billing;

import io.grayfile.domain.LlmModelEntity;
import io.grayfile.persistence.AuditExportStateRepository;
import io.grayfile.persistence.AuditLogRepository;
import io.grayfile.persistence.BillingWindowRepository;
import io.grayfile.persistence.LlmModelRepository;
import io.grayfile.persistence.UsageEventRepository;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.UserTransaction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
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
    LlmModelRepository llmModelRepository;

    @Inject
    AuditLogRepository auditLogRepository;

    @Inject
    AuditExportStateRepository auditExportStateRepository;

    @Inject
    UserTransaction userTransaction;

    @BeforeEach
    void cleanDatabase() throws Exception {
        userTransaction.begin();
        try {
            billingWindowRepository.deleteAll();
            usageEventRepository.deleteAll();
            auditLogRepository.deleteAll();
            auditExportStateRepository.deleteAll();
            llmModelRepository.deleteAll();
            LlmModelEntity model = new LlmModelEntity();
            model.id = "gpt-4o-mini";
            model.displayName = "GPT-4o Mini";
            model.provider = "openai";
            model.active = true;
            model.defaultTimeCriterionSeconds = 1;
            model.defaultTimePrice = BigDecimal.ZERO.setScale(6);
            model.defaultTokenCriterion = 1000;
            model.defaultTokenPrice = BigDecimal.ZERO.setScale(6);
            llmModelRepository.persist(model);
            userTransaction.commit();
        } catch (Exception exception) {
            userTransaction.rollback();
            throw exception;
        }
    }

    @Test
    void shouldDeduplicateReplayOnSameRequestId() {
        Instant eventTime = Instant.parse("2026-04-09T10:15:30Z");

        billingService.handleUsage("customer-1", "key-1", "gpt-4o-mini", "req-1", 1000, 40, 60, 100, "token", "tokens", 100D, "{}", CONTRACT_VERSION, EXTRACTOR_VERSION, USAGE_SIGNATURE, eventTime);
        billingService.handleUsage("customer-1", "key-1", "gpt-4o-mini", "req-1", 1000, 40, 60, 100, "token", "tokens", 100D, "{}", CONTRACT_VERSION, EXTRACTOR_VERSION, USAGE_SIGNATURE, eventTime.plusSeconds(1));

        assertEquals(1L, usageEventRepository.count());
        assertEquals(1L, billingWindowRepository.count());
        assertEquals(100, billingWindowRepository.listAll().getFirst().tokenTotal);
        assertEquals(1L, auditLogRepository.find("eventType", "BILLING_USAGE_DEDUPLICATED").count());
    }

    @Test
    void shouldIgnoreRetryWithChangedTokenPayloadOnSameRequestId() {
        Instant eventTime = Instant.parse("2026-04-09T11:15:30Z");

        billingService.handleUsage("customer-1", "key-1", "gpt-4o-mini", "req-2", 1000, 30, 20, 50, "token", "tokens", 50D, "{}", CONTRACT_VERSION, EXTRACTOR_VERSION, USAGE_SIGNATURE, eventTime);
        billingService.handleUsage("customer-1", "key-1", "gpt-4o-mini", "req-2", 1000, 300, 200, 500, "token", "tokens", 500D, "{}", CONTRACT_VERSION, EXTRACTOR_VERSION, USAGE_SIGNATURE, eventTime.plusSeconds(5));

        assertEquals(1L, usageEventRepository.count());
        assertEquals(50, usageEventRepository.listAll().getFirst().totalTokens);
        assertEquals(50, billingWindowRepository.listAll().getFirst().tokenTotal);
        assertEquals(1L, auditLogRepository.find("eventType", "BILLING_USAGE_DEDUPLICATED").count());
    }
}
