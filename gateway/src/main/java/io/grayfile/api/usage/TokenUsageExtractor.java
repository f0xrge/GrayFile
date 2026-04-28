package io.grayfile.api.usage;

import com.fasterxml.jackson.databind.JsonNode;
import io.grayfile.api.OpenAiEndpoint;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.Set;

@ApplicationScoped
public class TokenUsageExtractor implements UsageExtractor {

    private static final Set<OpenAiEndpoint> SUPPORTED = Set.of(
            OpenAiEndpoint.RESPONSES,
            OpenAiEndpoint.CHAT_COMPLETIONS,
            OpenAiEndpoint.COMPLETIONS
    );

    @Override
    public boolean supports(OpenAiEndpoint endpoint, JsonNode payload) {
        return SUPPORTED.contains(endpoint);
    }

    @Override
    public ExtractionResult extract(OpenAiEndpoint endpoint, JsonNode payload) {
        JsonNode usageNode = payload.path("usage");
        if (usageNode.isMissingNode() || usageNode.isNull()) {
            return new ExtractionResult(null, null, null, null, "token", true, "missing_usage");
        }

        Integer inputTokens = readNonNegativeInt(usageNode, "input_tokens");
        if (inputTokens == null) {
            inputTokens = readNonNegativeInt(usageNode, "prompt_tokens");
        }
        Integer outputTokens = readNonNegativeInt(usageNode, "output_tokens");
        if (outputTokens == null) {
            outputTokens = readNonNegativeInt(usageNode, "completion_tokens");
        }
        Integer totalTokens = readNonNegativeInt(usageNode, "total_tokens");

        if (inputTokens == null || outputTokens == null || totalTokens == null) {
            return new ExtractionResult(inputTokens, outputTokens, totalTokens, null, "token", true, "invalid_usage_payload");
        }

        return new ExtractionResult(inputTokens, outputTokens, totalTokens, null, "token", false, "captured");
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
