package io.grayfile.backend;

import jakarta.ws.rs.core.Response;

import java.util.Map;

public interface BackendGateway {
    Response proxy(String baseUrl, OpenAiRequestContext requestContext);

    default Response proxy(String baseUrl, OpenAiRequestContext requestContext, Map<String, String> headers) {
        return proxy(baseUrl, requestContext);
    }
}
