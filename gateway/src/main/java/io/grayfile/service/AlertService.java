package io.grayfile.service;

import jakarta.enterprise.context.ApplicationScoped;
import org.jboss.logging.Logger;

import java.time.Instant;
import java.util.Map;

@ApplicationScoped
public class AlertService {

    private static final Logger LOG = Logger.getLogger(AlertService.class);

    private final AuditLogService auditLogService;

    public AlertService(AuditLogService auditLogService) {
        this.auditLogService = auditLogService;
    }

    public void emitCritical(String alertType,
                             String entityType,
                             String entityId,
                             String actorId,
                             String requestId,
                             String sourceIp,
                             String reason,
                             Map<String, Object> details) {
        LOG.warnf("{\"event\":\"critical_alert\",\"alert_type\":\"%s\",\"entity_type\":\"%s\",\"entity_id\":\"%s\",\"actor_id\":\"%s\",\"request_id\":\"%s\"}",
                alertType,
                entityType,
                entityId,
                actorId,
                requestId);

        auditLogService.logEvent(
                "CRITICAL_ALERT_TRIGGERED",
                actorId,
                entityType,
                entityId,
                auditLogService.payloadOf(
                        "alert_type", alertType,
                        "request_id", requestId,
                        "source_ip", sourceIp,
                        "reason", reason,
                        "details", details
                ),
                Instant.now()
        );
    }
}
