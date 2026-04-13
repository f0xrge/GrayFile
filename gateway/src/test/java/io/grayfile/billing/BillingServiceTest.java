package io.grayfile.billing;

import io.grayfile.domain.BillingWindowEntity;
import io.grayfile.domain.UsageEventEntity;
import io.grayfile.metrics.BillingMetrics;
import io.grayfile.persistence.BillingWindowRepository;
import io.grayfile.persistence.UsageEventRepository;
import io.grayfile.service.AuditLogService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BillingServiceTest {
    private static final String CONTRACT_VERSION = "usage_extraction.v1";
    private static final String EXTRACTOR_VERSION = "gateway-backend-payload-v1";
    private static final String USAGE_SIGNATURE = "signature";

    @Mock
    UsageEventRepository usageEventRepository;

    @Mock
    BillingWindowRepository billingWindowRepository;

    @Mock
    BillingMetrics billingMetrics;

    @Mock
    AuditLogService auditLogService;

    BillingService billingService;

    @BeforeEach
    void setUp() {
        billingService = new BillingService(usageEventRepository, billingWindowRepository, billingMetrics, auditLogService);
    }

    @Test
    void shouldPersistUsageAndSkipWindowWhenTotalTokensIsZero() {
        Instant eventTime = Instant.parse("2026-04-09T10:00:00Z");
        when(usageEventRepository.findByBusinessKey("req-0", "customer-1", "key-1", "gpt-4o-mini"))
                .thenReturn(Optional.empty());

        billingService.handleUsage("customer-1", "key-1", "gpt-4o-mini", "req-0", 0, 0, 0, CONTRACT_VERSION, EXTRACTOR_VERSION, USAGE_SIGNATURE, eventTime);

        verify(usageEventRepository).persistAndFlush(any(UsageEventEntity.class));
        verify(billingMetrics).recordUsageEvent(0);
        verify(billingWindowRepository, never()).persist(any(BillingWindowEntity.class));
        verify(billingWindowRepository, never()).findActive(anyString(), anyString(), anyString());
        verify(billingMetrics, never()).recordWindowClose();
    }

    @Test
    void shouldSplitUsageAcrossMultipleBillingWindowsOnOverflow() {
        Instant eventTime = Instant.parse("2026-04-09T11:00:00Z");
        when(usageEventRepository.findByBusinessKey("req-overflow", "customer-1", "key-1", "gpt-4o-mini"))
                .thenReturn(Optional.empty());

        AtomicReference<BillingWindowEntity> activeWindow = new AtomicReference<>();
        List<BillingWindowEntity> persisted = new ArrayList<>();
        when(billingWindowRepository.findActive("customer-1", "key-1", "gpt-4o-mini"))
                .thenAnswer(invocation -> {
                    BillingWindowEntity value = activeWindow.get();
                    return value != null && value.active ? Optional.of(value) : Optional.empty();
                });
        doAnswer(invocation -> {
            BillingWindowEntity created = invocation.getArgument(0);
            activeWindow.set(created);
            persisted.add(created);
            return null;
        }).when(billingWindowRepository).persist(any(BillingWindowEntity.class));

        billingService.handleUsage("customer-1", "key-1", "gpt-4o-mini", "req-overflow", 1100, 1023, 2123, CONTRACT_VERSION, EXTRACTOR_VERSION, USAGE_SIGNATURE, eventTime);

        assertEquals(3, persisted.size());
        assertEquals(1000, persisted.get(0).tokenTotal);
        assertFalse(persisted.get(0).active);
        assertEquals("TOKEN_LIMIT", persisted.get(0).closureReason);

        assertEquals(1000, persisted.get(1).tokenTotal);
        assertFalse(persisted.get(1).active);
        assertEquals("TOKEN_LIMIT", persisted.get(1).closureReason);

        assertEquals(123, persisted.get(2).tokenTotal);
        assertTrue(persisted.get(2).active);
        assertNull(persisted.get(2).closureReason);

        verify(billingMetrics, times(2)).recordWindowClose();
        verify(billingMetrics).recordUsageEvent(2123);
    }

    @Test
    void shouldCloseExpiredWindowsAtTimeLimitBoundary() {
        Instant windowStart = Instant.parse("2026-04-09T09:00:00Z");
        Instant now = windowStart.plus(BillingService.TIME_LIMIT).plusSeconds(1);

        BillingWindowEntity active = new BillingWindowEntity();
        active.id = UUID.randomUUID();
        active.customerId = "customer-1";
        active.apiKeyId = "key-1";
        active.model = "gpt-4o-mini";
        active.windowStart = windowStart;
        active.tokenTotal = 200;
        active.active = true;

        when(billingWindowRepository.findExpiredActive(now.minus(BillingService.TIME_LIMIT)))
                .thenReturn(List.of(active));

        billingService.closeExpiredWindows(now);

        assertFalse(active.active);
        assertEquals(windowStart.plus(BillingService.TIME_LIMIT), active.windowEnd);
        assertEquals("TIME_LIMIT", active.closureReason);
        verify(billingMetrics).recordWindowClose();
    }

    @Test
    void shouldIgnoreDuplicateRequestIdBeforePersisting() {
        UsageEventEntity existing = new UsageEventEntity();
        existing.id = UUID.randomUUID();
        when(usageEventRepository.findByBusinessKey("req-dup", "customer-1", "key-1", "gpt-4o-mini"))
                .thenReturn(Optional.of(existing));

        billingService.handleUsage(
                "customer-1",
                "key-1",
                "gpt-4o-mini",
                "req-dup",
                10,
                5,
                15,
                CONTRACT_VERSION,
                EXTRACTOR_VERSION,
                USAGE_SIGNATURE,
                Instant.parse("2026-04-09T12:00:00Z")
        );

        verify(usageEventRepository, never()).persistAndFlush(any(UsageEventEntity.class));
        verify(billingMetrics, never()).recordUsageEvent(15);
        verify(billingWindowRepository, never()).persist(any(BillingWindowEntity.class));
    }

    @Test
    void shouldHandleDuplicateRequestIdWhenPersistRaceOccurs() {
        UsageEventEntity existing = new UsageEventEntity();
        existing.id = UUID.randomUUID();
        when(usageEventRepository.findByBusinessKey("req-race", "customer-1", "key-1", "gpt-4o-mini"))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(existing));
        doThrow(new RuntimeException("unique constraint")).when(usageEventRepository)
                .persistAndFlush(any(UsageEventEntity.class));

        billingService.handleUsage(
                "customer-1",
                "key-1",
                "gpt-4o-mini",
                "req-race",
                10,
                5,
                15,
                CONTRACT_VERSION,
                EXTRACTOR_VERSION,
                USAGE_SIGNATURE,
                Instant.parse("2026-04-09T13:00:00Z")
        );

        verify(billingMetrics, never()).recordUsageEvent(15);
        verify(billingWindowRepository, never()).persist(any(BillingWindowEntity.class));
        ArgumentCaptor<String> eventCaptor = ArgumentCaptor.forClass(String.class);
        verify(auditLogService, times(1)).logEvent(
                eventCaptor.capture(),
                anyString(),
                anyString(),
                anyString(),
                any(),
                any(Instant.class)
        );
        assertEquals("BILLING_USAGE_DEDUPLICATED", eventCaptor.getValue());
    }
}
