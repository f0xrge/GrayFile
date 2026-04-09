package io.grayfile.backend;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@ApplicationScoped
public class DynamicBackendGateway implements BackendGateway {

    private final Client client;

    public DynamicBackendGateway() {
        this.client = ClientBuilder.newBuilder().build();
    }

    @Override
    public Response chatCompletions(String baseUrl, String requestId, String traceparent, JsonNode requestBody) {
        var request = client.target(baseUrl)
                .path("/v1/chat/completions")
                .request(MediaType.APPLICATION_JSON_TYPE)
                .header("x-request-id", requestId);

        if (traceparent != null && !traceparent.isBlank()) {
            request.header("traceparent", traceparent);
        }

        return request.post(Entity.entity(requestBody, MediaType.APPLICATION_JSON_TYPE));
    }
}
