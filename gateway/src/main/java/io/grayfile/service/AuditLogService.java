package io.grayfile.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.grayfile.domain.AuditLogEntity;
import io.grayfile.persistence.AuditLogRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

@ApplicationScoped
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;
    private final EntityManager entityManager;
    private final ObjectMapper objectMapper;
    private final String signingKey;

    public AuditLogService(AuditLogRepository auditLogRepository,
                           EntityManager entityManager,
                           ObjectMapper objectMapper,
                           @ConfigProperty(name = "grayfile.audit.signing-key", defaultValue = "grayfile-dev-signing-key") String signingKey) {
        this.auditLogRepository = auditLogRepository;
        this.entityManager = entityManager;
        this.objectMapper = objectMapper;
        this.signingKey = signingKey;
    }

    @Transactional
    public void logEvent(String eventType,
                         String actor,
                         String entityType,
                         String entityId,
                         Map<String, Object> payload,
                         Instant occurredAt) {
        lockAuditLogTail();
        Optional<AuditLogEntity> previousEvent = auditLogRepository.findLatest();
        String prevHash = previousEvent.map(e -> e.eventHash).orElse(null);
        Instant timestamp = occurredAt == null ? Instant.now() : occurredAt;

        String payloadJson = toJson(payload);
        String eventHash = sha256(eventType + "|" + actor + "|" + entityType + "|" + entityId + "|" + payloadJson + "|" + timestamp + "|" + (prevHash == null ? "" : prevHash));
        String signature = hmacSha256(eventHash, signingKey);

        AuditLogEntity entity = new AuditLogEntity();
        entity.eventType = eventType;
        entity.actor = actor;
        entity.entityType = entityType;
        entity.entityId = entityId;
        entity.payloadJson = payloadJson;
        entity.occurredAt = timestamp;
        entity.prevHash = prevHash;
        entity.eventHash = eventHash;
        entity.signature = signature;
        auditLogRepository.persist(entity);
    }

    public Map<String, Object> payloadOf(Object... keyValuePairs) {
        Map<String, Object> payload = new LinkedHashMap<>();
        for (int i = 0; i + 1 < keyValuePairs.length; i += 2) {
            payload.put(String.valueOf(keyValuePairs[i]), keyValuePairs[i + 1]);
        }
        return payload;
    }

    private void lockAuditLogTail() {
        entityManager.createNativeQuery("SELECT event_id FROM audit_log ORDER BY event_id DESC LIMIT 1 FOR UPDATE")
                .getResultList();
    }

    private String toJson(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload == null ? Map.of() : payload);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("failed to serialize audit payload", exception);
        }
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return toHex(bytes);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    private String hmacSha256(String value, String key) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return toHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("failed to sign audit event", exception);
        }
    }

    private String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
