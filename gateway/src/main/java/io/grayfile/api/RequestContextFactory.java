package io.grayfile.api;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.core.HttpHeaders;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class RequestContextFactory {

    public RequestContext create(HttpHeaders headers) {
        return new RequestContext(
                resolveRequestId(headers),
                resolveTraceparent(headers),
                Instant.now()
        );
    }

    private String resolveRequestId(HttpHeaders headers) {
        return Optional.ofNullable(headers.getHeaderString("x-request-id"))
                .filter(value -> !value.isBlank())
                .orElseGet(() -> "req_" + UUID.randomUUID());
    }

    private String resolveTraceparent(HttpHeaders headers) {
        return Optional.ofNullable(headers.getHeaderString("traceparent"))
                .filter(value -> !value.isBlank())
                .orElse(null);
    }

    public record RequestContext(String requestId, String traceparent, Instant startedAt) {
    }
}
