package io.grayfile.service;

import io.grayfile.domain.AuditLogEntity;
import io.grayfile.persistence.AuditExportStateRepository;
import io.grayfile.persistence.AuditLogRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.List;

@ApplicationScoped
public class AuditExportService {

    private static final Logger LOG = Logger.getLogger(AuditExportService.class);

    private final AuditLogRepository auditLogRepository;
    private final AuditExportStateRepository auditExportStateRepository;
    private final ImmutableAuditObjectStore objectStore;
    private final int batchSize;

    public AuditExportService(AuditLogRepository auditLogRepository,
                              AuditExportStateRepository auditExportStateRepository,
                              ImmutableAuditObjectStore objectStore,
                              @ConfigProperty(name = "grayfile.audit.export.batch-size", defaultValue = "500") int batchSize) {
        this.auditLogRepository = auditLogRepository;
        this.auditExportStateRepository = auditExportStateRepository;
        this.objectStore = objectStore;
        this.batchSize = batchSize;
    }

    @Transactional
    public void exportPendingEvents() {
        AuditExportStateRepository.AuditExportStateEntity state = auditExportStateRepository.getOrCreate();
        List<AuditLogEntity> batch = auditLogRepository.findBatchAfter(state.lastExportedEventId, batchSize);
        if (batch.isEmpty()) {
            return;
        }

        String objectKey = "audit/" + DateTimeFormatter.ISO_INSTANT.format(Instant.now())
                + "-" + batch.getFirst().eventId + "-" + batch.getLast().eventId + ".ndjson";

        StringBuilder builder = new StringBuilder();
        for (AuditLogEntity event : batch) {
            builder.append(event.payloadJson)
                    .append("\t")
                    .append(event.eventHash)
                    .append("\t")
                    .append(event.signature)
                    .append("\n");
        }

        byte[] payload = builder.toString().getBytes(StandardCharsets.UTF_8);
        String checksum = sha256(payload);
        objectStore.writeImmutable(objectKey, payload, checksum);

        String actualChecksum = sha256(payload);
        if (!checksum.equals(actualChecksum)) {
            throw new IllegalStateException("audit export checksum mismatch");
        }

        state.lastExportedEventId = batch.getLast().eventId;
        state.updatedAt = Instant.now();
        LOG.infof("exported audit events [%d..%d] to immutable object store key=%s",
                batch.getFirst().eventId,
                batch.getLast().eventId,
                objectKey);
    }

    private String sha256(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(bytes);
            StringBuilder sb = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception exception) {
            throw new IllegalStateException("failed to compute checksum", exception);
        }
    }
}
