package io.grayfile.api;

import com.fasterxml.jackson.databind.JsonNode;
import io.grayfile.billing.BillingUsageHandler;
import io.grayfile.service.AuditLogService;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.time.Instant;
import java.util.Optional;

@ApplicationScoped
public class UsageCaptureService {

    private static final Logger LOG = Logger.getLogger(UsageCaptureService.class);

    private final BillingUsageHandler billingUsageHandler;
    private final AuditLogService auditLogService;
    private final String extractorVersion;
    private final String usageSigningKey;

    public UsageCaptureService(BillingUsageHandler billingUsageHandler,
                               AuditLogService auditLogService,
                               @ConfigProperty(name = "grayfile.usage.extractor.version", defaultValue = "gateway-backend-payload-v1") String extractorVersion,
                               @ConfigProperty(name = "grayfile.usage.signing-key", defaultValue = "grayfile-dev-usage-signing-key") String usageSigningKey) {
        this.billingUsageHandler = billingUsageHandler;
        this.auditLogService = auditLogService;
        this.extractorVersion = extractorVersion;
        this.usageSigningKey = usageSigningKey;
    }

    public UsageCaptureDecision captureUsage(String customerId,
                                             String apiKeyId,
                                             String requestId,
                                             JsonNode payload,
                                             EdgeUsageExtraction edgeUsageExtraction) {
        JsonNode usageNode = payload.path("usage");
        if (usageNode.isMissingNode() || usageNode.isNull()) {
            logExtractionAudit("missing_usage", requestId, payload.path("model").asText("unknown-model"));
            return UsageCaptureDecision.skippedReason("missing_usage");
        }

        Integer promptTokens = readNonNegativeInt(usageNode, "prompt_tokens");
        Integer completionTokens = readNonNegativeInt(usageNode, "completion_tokens");
        Integer totalTokens = readNonNegativeInt(usageNode, "total_tokens");
        if (promptTokens == null || completionTokens == null || totalTokens == null) {
            LOG.warnf("Skipping usage capture for request_id=%s due to invalid usage payload", requestId);
            logExtractionAudit("invalid_usage_payload", requestId, payload.path("model").asText("unknown-model"));
            return UsageCaptureDecision.skippedReason("invalid_usage_payload");
        }

        String model = payload.path("model").asText("unknown-model");
        UsageExtractionContract contract = UsageExtractionContract.of(
                requestId,
                model,
                promptTokens,
                completionTokens,
                totalTokens,
                extractorVersion
        );

        if (edgeUsageExtraction.present() && edgeUsageExtraction.hasDivergence(promptTokens, completionTokens, totalTokens)) {
            LOG.warnf(
                    "Rejecting usage capture for request_id=%s due to edge/backend usage divergence edge={prompt:%d,completion:%d,total:%d} backend={prompt:%d,completion:%d,total:%d}",
                    requestId,
                    edgeUsageExtraction.promptTokens(),
                    edgeUsageExtraction.completionTokens(),
                    edgeUsageExtraction.totalTokens(),
                    promptTokens,
                    completionTokens,
                    totalTokens
            );
            logExtractionAudit("edge_backend_divergence", requestId, model);
            return UsageCaptureDecision.divergentReason("edge_backend_divergence");
        }

        String signature = contract.signature(usageSigningKey);
        billingUsageHandler.handleUsage(
                customerId,
                apiKeyId,
                model,
                requestId,
                promptTokens,
                completionTokens,
                totalTokens,
                contract.contractVersion(),
                extractorVersion,
                signature,
                Instant.now()
        );
        return UsageCaptureDecision.capturedReason(contract.contractVersion(), extractorVersion);
    }

    private Integer readNonNegativeInt(JsonNode node, String fieldName) {
        JsonNode field = node.path(fieldName);
        if (!field.isInt() || field.asInt() < 0) {
            return null;
        }
        return field.asInt();
    }

    public record UsageCaptureDecision(boolean captured,
                                       boolean divergenceDetected,
                                       String reason,
                                       String contractVersion,
                                       String extractorVersion) {

        static UsageCaptureDecision capturedReason(String contractVersion, String extractorVersion) {
            return new UsageCaptureDecision(true, false, "captured", contractVersion, extractorVersion);
        }

        static UsageCaptureDecision skippedReason(String reason) {
            return new UsageCaptureDecision(false, false, reason, null, null);
        }

        static UsageCaptureDecision divergentReason(String reason) {
            return new UsageCaptureDecision(false, true, reason, null, null);
        }
    }

    private void logExtractionAudit(String reason, String requestId, String model) {
        auditLogService.logEvent(
                "USAGE_EXTRACTION_AUDIT",
                "usage-capture-service",
                "usage_extraction",
                requestId,
                auditLogService.payloadOf(
                        "reason", reason,
                        "request_id", requestId,
                        "model", model
                ),
                Instant.now()
        );
    }

    public record EdgeUsageExtraction(boolean present, int promptTokens, int completionTokens, int totalTokens) {

        public static EdgeUsageExtraction absent() {
            return new EdgeUsageExtraction(false, -1, -1, -1);
        }

        public static EdgeUsageExtraction fromHeaders(String promptHeader, String completionHeader, String totalHeader) {
            Optional<Integer> promptTokens = parseNonNegativeInt(promptHeader);
            Optional<Integer> completionTokens = parseNonNegativeInt(completionHeader);
            Optional<Integer> totalTokens = parseNonNegativeInt(totalHeader);
            if (promptTokens.isEmpty() || completionTokens.isEmpty() || totalTokens.isEmpty()) {
                return absent();
            }
            return new EdgeUsageExtraction(true, promptTokens.get(), completionTokens.get(), totalTokens.get());
        }

        private static Optional<Integer> parseNonNegativeInt(String value) {
            if (value == null || value.isBlank()) {
                return Optional.empty();
            }
            try {
                int parsed = Integer.parseInt(value);
                return parsed < 0 ? Optional.empty() : Optional.of(parsed);
            } catch (NumberFormatException ignored) {
                return Optional.empty();
            }
        }

        public boolean hasDivergence(int prompt, int completion, int total) {
            if (!present) {
                return false;
            }
            return promptTokens != prompt || completionTokens != completion || totalTokens != total;
        }
    }
}
