package io.grayfile.api.usage;

import com.fasterxml.jackson.databind.JsonNode;
import io.grayfile.api.OpenAiEndpoint;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class EmbeddingsUsageExtractor implements UsageExtractor {

    @Override
    public boolean supports(OpenAiEndpoint endpoint, JsonNode payload) {
        return endpoint == OpenAiEndpoint.EMBEDDINGS;
    }

    @Override
    public ExtractionResult extract(OpenAiEndpoint endpoint, JsonNode payload) {
        JsonNode usageNode = payload.path("usage");
        if (usageNode.isMissingNode() || usageNode.isNull()) {
            return new ExtractionResult(null, null, null, null, "embedding", true, "missing_usage");
        }

        Integer inputTokens = readNonNegativeInt(usageNode, "input_tokens");
        if (inputTokens == null) {
            inputTokens = readNonNegativeInt(usageNode, "prompt_tokens");
        }
        Integer totalTokens = readNonNegativeInt(usageNode, "total_tokens");
        if (totalTokens == null) {
            totalTokens = inputTokens;
        }

        if (inputTokens == null || totalTokens == null) {
            return new ExtractionResult(inputTokens, null, totalTokens, null, "embedding", true, "invalid_usage_payload");
        }

        return new ExtractionResult(inputTokens, 0, totalTokens, null, "embedding", false, "captured");
    }

    private Integer readNonNegativeInt(JsonNode node, String fieldName) {
        JsonNode field = node.path(fieldName);
        if (!field.isIntegralNumber()) {
            return null;
        }
        int value = field.asInt();
        return value < 0 ? null : value;
    }
}
