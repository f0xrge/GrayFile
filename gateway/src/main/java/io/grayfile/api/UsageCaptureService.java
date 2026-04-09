package io.grayfile.api;

import com.fasterxml.jackson.databind.JsonNode;
import io.grayfile.billing.BillingUsageHandler;
import org.jboss.logging.Logger;

import jakarta.enterprise.context.ApplicationScoped;
import java.time.Instant;

@ApplicationScoped
public class UsageCaptureService {

    private static final Logger LOG = Logger.getLogger(UsageCaptureService.class);

    private final BillingUsageHandler billingUsageHandler;

    public UsageCaptureService(BillingUsageHandler billingUsageHandler) {
        this.billingUsageHandler = billingUsageHandler;
    }

    public void captureUsage(String customerId, String apiKeyId, String requestId, JsonNode payload) {
        JsonNode usageNode = payload.path("usage");
        if (usageNode.isMissingNode() || usageNode.isNull()) {
            return;
        }

        Integer promptTokens = readNonNegativeInt(usageNode, "prompt_tokens");
        Integer completionTokens = readNonNegativeInt(usageNode, "completion_tokens");
        Integer totalTokens = readNonNegativeInt(usageNode, "total_tokens");
        if (promptTokens == null || completionTokens == null || totalTokens == null) {
            LOG.warnf("Skipping usage capture for request_id=%s due to invalid usage payload", requestId);
            return;
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
    }

    private Integer readNonNegativeInt(JsonNode node, String fieldName) {
        JsonNode field = node.path(fieldName);
        if (!field.isInt() || field.asInt() < 0) {
            return null;
        }
        return field.asInt();
    }
}
