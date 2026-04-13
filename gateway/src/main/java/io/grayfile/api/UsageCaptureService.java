package io.grayfile.api;

import com.fasterxml.jackson.databind.JsonNode;
import io.grayfile.billing.BillingUsageHandler;
import jakarta.enterprise.context.ApplicationScoped;
import org.jboss.logging.Logger;

import java.time.Instant;
import java.util.Optional;

@ApplicationScoped
public class UsageCaptureService {

    private static final Logger LOG = Logger.getLogger(UsageCaptureService.class);

    private final BillingUsageHandler billingUsageHandler;

    public UsageCaptureService(BillingUsageHandler billingUsageHandler) {
        this.billingUsageHandler = billingUsageHandler;
    }

    public UsageCaptureDecision captureUsage(String customerId,
                                             String apiKeyId,
                                             String requestId,
                                             JsonNode payload,
                                             EdgeUsageExtraction edgeUsageExtraction) {
        JsonNode usageNode = payload.path("usage");
        if (usageNode.isMissingNode() || usageNode.isNull()) {
            return UsageCaptureDecision.skippedReason("missing_usage");
        }

        Integer promptTokens = readNonNegativeInt(usageNode, "prompt_tokens");
        Integer completionTokens = readNonNegativeInt(usageNode, "completion_tokens");
        Integer totalTokens = readNonNegativeInt(usageNode, "total_tokens");
        if (promptTokens == null || completionTokens == null || totalTokens == null) {
            LOG.warnf("Skipping usage capture for request_id=%s due to invalid usage payload", requestId);
            return UsageCaptureDecision.skippedReason("invalid_usage_payload");
        }

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
            return UsageCaptureDecision.divergentReason("edge_backend_divergence");
        }

        String model = payload.path("model").asText("unknown-model");
        billingUsageHandler.handleUsage(
                customerId,
                apiKeyId,
                model,
                requestId,
                promptTokens,
                completionTokens,
                totalTokens,
                Instant.now()
        );
        return UsageCaptureDecision.capturedReason();
    }

    private Integer readNonNegativeInt(JsonNode node, String fieldName) {
        JsonNode field = node.path(fieldName);
        if (!field.isInt() || field.asInt() < 0) {
            return null;
        }
        return field.asInt();
    }

    public record UsageCaptureDecision(boolean captured, boolean divergenceDetected, String reason) {

        static UsageCaptureDecision capturedReason() {
            return new UsageCaptureDecision(true, false, "captured");
        }

        static UsageCaptureDecision skippedReason(String reason) {
            return new UsageCaptureDecision(false, false, reason);
        }

        static UsageCaptureDecision divergentReason(String reason) {
            return new UsageCaptureDecision(false, true, reason);
        }
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
