package io.grayfile.api;

import com.fasterxml.jackson.databind.JsonNode;
import io.grayfile.api.usage.UsageExtractor;
import io.grayfile.billing.BillingUsageHandler;
import io.grayfile.service.AuditLogService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class UsageCaptureService {

    private static final Logger LOG = Logger.getLogger(UsageCaptureService.class);

    private final BillingUsageHandler billingUsageHandler;
    private final AuditLogService auditLogService;
    private final String extractorVersion;
    private final String usageSigningKey;
    private final List<UsageExtractor> usageExtractors;

    public UsageCaptureService(BillingUsageHandler billingUsageHandler,
                               AuditLogService auditLogService,
                               Instance<UsageExtractor> usageExtractors,
                               @ConfigProperty(name = "grayfile.usage.extractor.version", defaultValue = "gateway-backend-payload-v1") String extractorVersion,
                               @ConfigProperty(name = "grayfile.usage.signing-key", defaultValue = "grayfile-dev-usage-signing-key") String usageSigningKey) {
        this.billingUsageHandler = billingUsageHandler;
        this.auditLogService = auditLogService;
        this.usageExtractors = usageExtractors.stream()
                .sorted(Comparator.comparing(extractor -> extractor.getClass().getSimpleName()))
                .toList();
        this.extractorVersion = extractorVersion;
        this.usageSigningKey = usageSigningKey;
    }

    public UsageCaptureDecision captureUsage(String customerId,
                                             String apiKeyId,
                                             String requestId,
                                             long durationMs,
                                             OpenAiEndpoint endpoint,
                                             JsonNode payload,
                                             EdgeUsageExtraction edgeUsageExtraction) {
        String model = payload.path("model").asText("unknown-model");
        Optional<UsageExtractor> selectedExtractor = usageExtractors.stream()
                .filter(extractor -> extractor.supports(endpoint, payload))
                .findFirst();
        if (selectedExtractor.isEmpty()) {
            logExtractionAudit("missing_usage_extractor", requestId, model, endpoint, null, null);
            return UsageCaptureDecision.skippedReason("missing_usage_extractor");
        }

        UsageExtractor.ExtractionResult extraction = selectedExtractor.get().extract(endpoint, payload);
        if (extraction.ambiguous()) {
            LOG.warnf("Skipping usage capture for request_id=%s endpoint=%s reason=%s", requestId, endpoint, extraction.reason());
            logExtractionAudit(extraction.reason(), requestId, model, endpoint, extraction.endpointType(), extraction.billableUnits());
            return UsageCaptureDecision.skippedReason(extraction.reason());
        }

        Integer inputTokens = extraction.inputTokens();
        Integer outputTokens = extraction.outputTokens();
        Integer totalTokens = extraction.totalTokens();

        if (extraction.hasTokenUsage() && (inputTokens == null || outputTokens == null || totalTokens == null)) {
            logExtractionAudit("invalid_usage_payload", requestId, model, endpoint, extraction.endpointType(), extraction.billableUnits());
            return UsageCaptureDecision.skippedReason("invalid_usage_payload");
        }

        if (edgeUsageExtraction.present() && extraction.hasTokenUsage()
                && edgeUsageExtraction.hasDivergence(inputTokens, outputTokens, totalTokens)) {
            LOG.warnf(
                    "Rejecting usage capture for request_id=%s due to edge/backend usage divergence edge={input:%d,output:%d,total:%d} backend={input:%d,output:%d,total:%d}",
                    requestId,
                    edgeUsageExtraction.inputTokens(),
                    edgeUsageExtraction.outputTokens(),
                    edgeUsageExtraction.totalTokens(),
                    inputTokens,
                    outputTokens,
                    totalTokens
            );
            logExtractionAudit("edge_backend_divergence", requestId, model, endpoint, extraction.endpointType(), extraction.billableUnits());
            return UsageCaptureDecision.divergentReason("edge_backend_divergence");
        }

        UsageExtractionContract contract = UsageExtractionContract.of(
                requestId,
                model,
                inputTokens,
                outputTokens,
                totalTokens,
                extraction.billableUnits(),
                extraction.endpointType(),
                extractorVersion
        );

        String signature = contract.signature(usageSigningKey);
        billingUsageHandler.handleUsage(
                customerId,
                apiKeyId,
                model,
                requestId,
                durationMs,
                inputTokens == null ? 0 : inputTokens,
                outputTokens == null ? 0 : outputTokens,
                totalTokens == null ? 0 : totalTokens,
                contract.contractVersion(),
                extractorVersion,
                signature,
                Instant.now()
        );
        return UsageCaptureDecision.capturedReason(contract.contractVersion(), extractorVersion);
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

    private void logExtractionAudit(String reason,
                                    String requestId,
                                    String model,
                                    OpenAiEndpoint endpoint,
                                    String endpointType,
                                    Double billableUnits) {
        auditLogService.logEvent(
                "USAGE_EXTRACTION_AUDIT",
                "usage-capture-service",
                "usage_extraction",
                requestId,
                auditLogService.payloadOf(
                        "reason", reason,
                        "request_id", requestId,
                        "model", model,
                        "endpoint", endpoint.path(),
                        "endpoint_type", endpointType,
                        "billable_units", billableUnits
                ),
                Instant.now()
        );
    }

    public record EdgeUsageExtraction(boolean present, int inputTokens, int outputTokens, int totalTokens) {

        public static EdgeUsageExtraction absent() {
            return new EdgeUsageExtraction(false, -1, -1, -1);
        }

        public static EdgeUsageExtraction fromHeaders(String inputHeader, String outputHeader, String totalHeader) {
            Optional<Integer> inputTokens = parseNonNegativeInt(inputHeader);
            Optional<Integer> outputTokens = parseNonNegativeInt(outputHeader);
            Optional<Integer> totalTokens = parseNonNegativeInt(totalHeader);
            if (inputTokens.isEmpty() || outputTokens.isEmpty() || totalTokens.isEmpty()) {
                return absent();
            }
            return new EdgeUsageExtraction(true, inputTokens.get(), outputTokens.get(), totalTokens.get());
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

        public boolean hasDivergence(int input, int output, int total) {
            if (!present) {
                return false;
            }
            return inputTokens != input || outputTokens != output || totalTokens != total;
        }
    }
}
