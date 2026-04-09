package io.grayfile.api;

import com.fasterxml.jackson.databind.JsonNode;
import io.grayfile.backend.BackendClient;
import io.grayfile.billing.BillingService;
import io.grayfile.metrics.GatewayMetrics;
import io.grayfile.service.AuditLogService;
import io.grayfile.service.ManagementService;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.jboss.logging.Logger;
import org.eclipse.microprofile.rest.client.inject.RestClient;

import java.time.Instant;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

@Path("/llm/v1")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class LlmProxyResource {

    private static final Logger LOG = Logger.getLogger(LlmProxyResource.class);

    private final BackendClient backendClient;
    private final BillingService billingService;
    private final ManagementService managementService;
    private final GatewayMetrics gatewayMetrics;
    private final AuditLogService auditLogService;

    public LlmProxyResource(@RestClient BackendClient backendClient,
                            BillingService billingService,
                            ManagementService managementService,
                            GatewayMetrics gatewayMetrics,
                            AuditLogService auditLogService) {
        this.backendClient = backendClient;
        this.billingService = billingService;
        this.managementService = managementService;
        this.gatewayMetrics = gatewayMetrics;
        this.auditLogService = auditLogService;
    }

    @POST
    @Path("/chat/completions")
    public Response createCompletion(JsonNode requestBody,
                                     @HeaderParam("x-customer-id") String customerId,
                                     @HeaderParam("x-api-key-id") String apiKeyId,
                                     @Context HttpHeaders headers) {
        if (customerId == null || customerId.isBlank() || apiKeyId == null || apiKeyId.isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("x-customer-id and x-api-key-id headers are required")
                    .build();
        }
        String modelId = Optional.ofNullable(requestBody)
                .map(body -> body.path("model").asText(null))
                .filter(model -> !model.isBlank())
                .orElse(null);
        if (modelId == null) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("request body must contain a non-empty model")
                    .build();
        }

        ManagementService.UsageScopeValidation validation = managementService.validateUsageScope(customerId, apiKeyId, modelId);
        if (!validation.valid()) {
            gatewayMetrics.recordApplicationError("scope_validation", modelId, String.valueOf(Response.Status.BAD_REQUEST.getStatusCode()));
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(validation.message())
                    .build();
        }

        String requestId = resolveRequestId(headers);
        String traceparent = resolveTraceparent(headers);
        Instant startedAt = Instant.now();

        auditLogService.logEvent(
                "MODEL_ROUTING_DECISION",
                "gateway-router",
                "routing",
                requestId,
                auditLogService.payloadOf(
                        "customer_id", customerId,
                        "api_key_id", apiKeyId,
                        "model", modelId,
                        "decision", "route_to_backend"
                ),
                startedAt
        );

        try (Response backendResponse = backendClient.chatCompletions(requestId, traceparent, requestBody)) {
            JsonNode payload = backendResponse.readEntity(JsonNode.class);
            captureUsage(customerId, apiKeyId, requestId, payload);
            observeAndLog(startedAt, requestId, customerId, apiKeyId, modelId, backendResponse);
            return Response.status(backendResponse.getStatus())
                    .entity(payload)
                    .header("x-grayfile-gateway", "grayfile-gateway")
                    .header("x-request-id", requestId)
                    .build();
        } catch (Exception exception) {
            long latencyNanos = Duration.between(startedAt, Instant.now()).toNanos();
            gatewayMetrics.recordRequestLatency(modelId, customerId, apiKeyId, 500, latencyNanos);
            gatewayMetrics.recordApplicationError("gateway_exception", modelId, "500");
            LOG.errorf(exception,
                    "{\"event\":\"gateway_request\",\"request_id\":\"%s\",\"customer_id\":\"%s\",\"api_key_id\":\"%s\",\"model\":\"%s\",\"backend_status\":500,\"latency_ms\":%d}",
                    requestId,
                    customerId,
                    apiKeyId,
                    modelId,
                    Duration.ofNanos(latencyNanos).toMillis());
            return Response.serverError()
                    .entity("gateway failed to call backend")
                    .header("x-request-id", requestId)
                    .build();
        }
    }

    private void observeAndLog(Instant startedAt,
                               String requestId,
                               String customerId,
                               String apiKeyId,
                               String modelId,
                               Response backendResponse) {
        int backendStatus = backendResponse.getStatus();
        long latencyNanos = Duration.between(startedAt, Instant.now()).toNanos();
        gatewayMetrics.recordRequestLatency(modelId, customerId, apiKeyId, backendStatus, latencyNanos);

        String responseFlags = Optional.ofNullable(backendResponse.getHeaderString("x-envoy-response-flags")).orElse("");
        int attemptCount = parseAttemptCount(backendResponse.getHeaderString("x-envoy-attempt-count"));

        if (attemptCount > 1) {
            gatewayMetrics.recordEdgeError("retry", modelId, String.valueOf(backendStatus));
        }

        String edgeErrorType = mapEdgeErrorType(backendStatus, responseFlags);
        if (edgeErrorType != null) {
            gatewayMetrics.recordEdgeError(edgeErrorType, modelId, String.valueOf(backendStatus));
        } else if (backendStatus >= 400) {
            gatewayMetrics.recordApplicationError("backend_status", modelId, String.valueOf(backendStatus));
        }

        LOG.infof(
                "{\"event\":\"gateway_request\",\"request_id\":\"%s\",\"customer_id\":\"%s\",\"api_key_id\":\"%s\",\"model\":\"%s\",\"backend_status\":%d,\"latency_ms\":%d}",
                requestId,
                customerId,
                apiKeyId,
                modelId,
                backendStatus,
                Duration.ofNanos(latencyNanos).toMillis()
        );
    }

    private String mapEdgeErrorType(int backendStatus, String responseFlags) {
        if (backendStatus == 504 || responseFlags.contains("UT")) {
            return "timeout";
        }
        if (responseFlags.contains("UO")) {
            return "circuit_open";
        }
        if (backendStatus >= 502 && backendStatus <= 504) {
            return "upstream_unavailable";
        }
        return null;
    }

    private int parseAttemptCount(String attemptCountHeader) {
        if (attemptCountHeader == null || attemptCountHeader.isBlank()) {
            return 1;
        }
        try {
            return Integer.parseInt(attemptCountHeader);
        } catch (NumberFormatException ignored) {
            return 1;
        }
    }

    private void captureUsage(String customerId, String apiKeyId, String requestId, JsonNode payload) {
        JsonNode usageNode = payload.path("usage");
        if (usageNode.isMissingNode()) {
            return;
        }

        billingService.handleUsage(
                customerId,
                apiKeyId,
                payload.path("model").asText("unknown-model"),
                requestId,
                usageNode.path("prompt_tokens").asInt(0),
                usageNode.path("completion_tokens").asInt(0),
                usageNode.path("total_tokens").asInt(0),
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
}
