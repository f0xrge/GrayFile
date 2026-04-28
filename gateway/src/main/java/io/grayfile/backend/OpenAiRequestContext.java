package io.grayfile.backend;

import com.fasterxml.jackson.databind.JsonNode;
import io.grayfile.api.OpenAiEndpoint;

import java.time.Instant;

public record OpenAiRequestContext(OpenAiEndpoint endpoint,
                                   String httpMethod,
                                   String customerId,
                                   String apiKeyId,
                                   String modelId,
                                   JsonNode requestBody,
                                   String requestId,
                                   String traceparent,
                                   Instant startedAt) {
}
