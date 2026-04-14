package io.grayfile.api;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.core.HttpHeaders;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

@ApplicationScoped
public class RequestContextFactory {

    private static final Pattern REQUEST_ID_PATTERN = Pattern.compile("^[A-Za-z0-9._:-]{1,120}$");
    private static final Pattern TRACEPARENT_PATTERN = Pattern.compile("^[0-9a-fA-F]{2}-[0-9a-fA-F]{32}-[0-9a-fA-F]{16}-[0-9a-fA-F]{2}$");

    public RequestContext create(HttpHeaders headers) {
        return new RequestContext(
                resolveRequestId(headers),
                resolveTraceparent(headers),
                Instant.now()
        );
    }

    private String resolveRequestId(HttpHeaders headers) {
        return Optional.ofNullable(headers.getHeaderString("x-request-id"))
                .map(String::trim)
                .filter(value -> REQUEST_ID_PATTERN.matcher(value).matches())
                .orElseGet(() -> "req_" + UUID.randomUUID());
    }

    private String resolveTraceparent(HttpHeaders headers) {
        return Optional.ofNullable(headers.getHeaderString("traceparent"))
                .map(String::trim)
                .filter(value -> TRACEPARENT_PATTERN.matcher(value).matches())
                .orElse(null);
    }

    public record RequestContext(String requestId, String traceparent, Instant startedAt) {
    }
}
