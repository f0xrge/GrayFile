package io.grayfile.litellm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.ProcessingException;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@ApplicationScoped
public class LiteLlmAdminClient {

    private final Client client;
    private final ObjectMapper objectMapper;
    private final String baseUrl;
    private final String masterKey;
    private final boolean syncEnabled;
    private final boolean dryRun;

    public LiteLlmAdminClient(ObjectMapper objectMapper,
                              @ConfigProperty(name = "grayfile.litellm.base-url") String baseUrl,
                              @ConfigProperty(name = "grayfile.litellm.master-key") String masterKey,
                              @ConfigProperty(name = "grayfile.litellm.sync.enabled") boolean syncEnabled,
                              @ConfigProperty(name = "grayfile.litellm.sync.dry-run") boolean dryRun,
                              @ConfigProperty(name = "grayfile.litellm.timeout") Duration timeout) {
        this.objectMapper = objectMapper;
        this.baseUrl = trimTrailingSlash(baseUrl);
        this.masterKey = masterKey;
        this.syncEnabled = syncEnabled;
        this.dryRun = dryRun;
        this.client = ClientBuilder.newBuilder()
                .connectTimeout(timeout.toMillis(), TimeUnit.MILLISECONDS)
                .readTimeout(timeout.toMillis(), TimeUnit.MILLISECONDS)
                .build();
    }

    public LiteLlmCallResult health() {
        if (!syncEnabled || dryRun) {
            return LiteLlmCallResult.success("dry-run");
        }
        return request("GET", "/health", null);
    }

    public LiteLlmCallResult upsertModel(Map<String, Object> payload) {
        return request("POST", "/model/new", payload);
    }

    public LiteLlmCallResult generateKey(Map<String, Object> payload) {
        return request("POST", "/key/generate", payload);
    }

    public LiteLlmCallResult blockKey(String key) {
        return request("POST", "/key/block", Map.of("key", key));
    }

    public boolean syncEnabled() {
        return syncEnabled;
    }

    public boolean dryRun() {
        return dryRun;
    }

    private LiteLlmCallResult request(String method, String path, Object payload) {
        if (!syncEnabled || dryRun) {
            return LiteLlmCallResult.success("dry-run");
        }
        try (Response response = switch (method) {
            case "GET" -> client.target(baseUrl)
                    .path(normalizePath(path))
                    .request(MediaType.APPLICATION_JSON_TYPE)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + masterKey)
                    .get();
            case "POST" -> client.target(baseUrl)
                    .path(normalizePath(path))
                    .request(MediaType.APPLICATION_JSON_TYPE)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + masterKey)
                    .post(Entity.entity(payload, MediaType.APPLICATION_JSON_TYPE));
            default -> throw new IllegalArgumentException("unsupported LiteLLM method: " + method);
        }) {
            String body = response.hasEntity() ? response.readEntity(String.class) : "";
            if (response.getStatus() >= 200 && response.getStatus() < 300) {
                return LiteLlmCallResult.success(extractResourceId(body));
            }
            return LiteLlmCallResult.failure("LiteLLM " + path + " returned HTTP " + response.getStatus() + ": " + truncate(body));
        } catch (ProcessingException | IllegalArgumentException exception) {
            return LiteLlmCallResult.failure(exception.getMessage());
        }
    }

    private String extractResourceId(String body) {
        if (body == null || body.isBlank()) {
            return null;
        }
        try {
            JsonNode json = objectMapper.readTree(body);
            for (String field : new String[]{"key", "token", "model_id", "id"}) {
                String value = json.path(field).asText(null);
                if (value != null && !value.isBlank()) {
                    return value;
                }
            }
        } catch (Exception ignored) {
            return null;
        }
        return null;
    }

    private String truncate(String value) {
        if (value == null) {
            return "";
        }
        return value.length() <= 500 ? value : value.substring(0, 500);
    }

    private String normalizePath(String path) {
        if (path == null || path.isBlank()) {
            return "";
        }
        return path.startsWith("/") ? path.substring(1) : path;
    }

    private String trimTrailingSlash(String value) {
        if (value == null || value.isBlank()) {
            return "http://localhost:4000";
        }
        String trimmed = value.trim();
        return trimmed.endsWith("/") ? trimmed.substring(0, trimmed.length() - 1) : trimmed;
    }

    @PreDestroy
    void destroy() {
        client.close();
    }

    public record LiteLlmCallResult(boolean success, String resourceId, String error) {
        static LiteLlmCallResult success(String resourceId) {
            return new LiteLlmCallResult(true, resourceId, null);
        }

        static LiteLlmCallResult failure(String error) {
            return new LiteLlmCallResult(false, null, error);
        }
    }
}
