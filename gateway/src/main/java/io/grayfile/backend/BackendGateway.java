package io.grayfile.backend;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.ws.rs.core.Response;

public interface BackendGateway {
    Response chatCompletions(String baseUrl, String requestId, String traceparent, JsonNode requestBody);
}
