package io.grayfile.service;

import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class AuditExportScheduler {

    private final AuditExportService auditExportService;

    public AuditExportScheduler(AuditExportService auditExportService) {
        this.auditExportService = auditExportService;
    }

    @Scheduled(every = "{grayfile.audit.export.every:15m}")
    void runPeriodicExport() {
        auditExportService.exportPendingEvents();
    }
}
