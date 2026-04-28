package io.grayfile.api.usage;

import com.fasterxml.jackson.databind.JsonNode;
import io.grayfile.api.OpenAiEndpoint;

public interface UsageExtractor {

    boolean supports(OpenAiEndpoint endpoint, JsonNode payload);

    ExtractionResult extract(OpenAiEndpoint endpoint, JsonNode payload);

    record ExtractionResult(Integer inputTokens,
                            Integer outputTokens,
                            Integer totalTokens,
                            Double billableUnits,
                            String endpointType,
                            boolean ambiguous,
                            String reason) {

        public boolean hasTokenUsage() {
            return inputTokens != null || outputTokens != null || totalTokens != null;
        }

        public boolean hasBillableUsage() {
            return hasTokenUsage() || billableUnits != null;
        }
    }
}
