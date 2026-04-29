package io.grayfile.backend;

import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
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
    public Response proxy(String baseUrl, OpenAiRequestContext requestContext) {
        return proxy(baseUrl, requestContext, Map.of());
    }

    @Override
    public Response proxy(String baseUrl, OpenAiRequestContext requestContext, Map<String, String> headers) {
        Client client = clientsByBaseUrl.computeIfAbsent(baseUrl, ignored -> ClientBuilder.newBuilder().build());
        var request = client.target(baseUrl)
                .path(requestContext.endpoint().path())
                .request(MediaType.WILDCARD_TYPE)
                .header("x-request-id", requestContext.requestId());

        if (requestContext.traceparent() != null && !requestContext.traceparent().isBlank()) {
            request.header("traceparent", requestContext.traceparent());
        }
        headers.forEach((key, value) -> {
            if (value != null && !value.isBlank()) {
                request.header(key, value);
            }
        });

        return switch (requestContext.httpMethod().toUpperCase()) {
            case "GET" -> request.get();
            case "POST" -> request.post(Entity.entity(requestContext.requestBody(), MediaType.APPLICATION_JSON_TYPE));
            default -> throw new IllegalArgumentException("unsupported http method: " + requestContext.httpMethod());
        };
    }

    @PreDestroy
    void destroy() {
        clientsByBaseUrl.values().forEach(Client::close);
        clientsByBaseUrl.clear();
    }
}
