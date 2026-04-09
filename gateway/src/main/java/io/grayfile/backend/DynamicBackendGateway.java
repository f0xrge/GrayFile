package io.grayfile.backend;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.annotation.PreDestroy;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@ApplicationScoped
public class DynamicBackendGateway implements BackendGateway {

    private final Map<String, Client> clientsByBaseUrl = new ConcurrentHashMap<>();

    @Override
    public Response chatCompletions(String baseUrl, String requestId, String traceparent, JsonNode requestBody) {
        Client client = clientsByBaseUrl.computeIfAbsent(baseUrl, ignored -> ClientBuilder.newBuilder().build());
        var request = client.target(baseUrl)
                .path("/v1/chat/completions")
                .request(MediaType.APPLICATION_JSON_TYPE)
                .header("x-request-id", requestId);

        if (traceparent != null && !traceparent.isBlank()) {
            request.header("traceparent", traceparent);
        }

        return request.post(Entity.entity(requestBody, MediaType.APPLICATION_JSON_TYPE));
    }

    @PreDestroy
    void destroy() {
        clientsByBaseUrl.values().forEach(Client::close);
        clientsByBaseUrl.clear();
    }
}
