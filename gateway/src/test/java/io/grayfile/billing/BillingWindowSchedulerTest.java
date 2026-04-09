package io.grayfile.billing;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class BillingWindowSchedulerTest {

    @Mock
    BillingService billingService;

    @Test
    void shouldDelegateExpiredWindowClosingWithCurrentInstant() {
        BillingWindowScheduler scheduler = new BillingWindowScheduler(billingService);
        Instant before = Instant.now();

        scheduler.closeExpiredWindows();

        Instant after = Instant.now();
        ArgumentCaptor<Instant> captor = ArgumentCaptor.forClass(Instant.class);
        verify(billingService).closeExpiredWindows(captor.capture());
        Instant forwarded = captor.getValue();
        assertFalse(forwarded.isBefore(before));
        assertFalse(forwarded.isAfter(after));
    }
}
