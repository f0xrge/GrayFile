package io.grayfile.billing;

import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;

import java.time.Instant;

@ApplicationScoped
public class BillingWindowScheduler {

    private final BillingService billingService;

    public BillingWindowScheduler(BillingService billingService) {
        this.billingService = billingService;
    }

    @Scheduled(every = "30s")
    void closeExpiredWindows() {
        billingService.closeExpiredWindows(Instant.now());
    }
}
