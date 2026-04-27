package io.grayfile.backend;

import jakarta.ws.rs.core.Response;

public interface BackendGateway {
    Response proxy(String baseUrl, OpenAiRequestContext requestContext);
}
