package io.grayfile.api;

import com.fasterxml.jackson.databind.JsonNode;
import io.grayfile.backend.BackendGateway;
import io.grayfile.metrics.GatewayMetricsRecorder;
import io.grayfile.service.AuditLogService;
import io.grayfile.service.ManagementService;
import io.grayfile.service.ModelRoutingService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.core.Response;
import org.jboss.logging.Logger;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

@ApplicationScoped
public class InferenceOrchestrator {

    private static final Logger LOG = Logger.getLogger(InferenceOrchestrator.class);

    private final BackendGateway backendGateway;
    private final ModelRoutingService modelRoutingService;
    private final ManagementService managementService;
    private final GatewayMetricsRecorder gatewayMetrics;
    private final AuditLogService auditLogService;
    private final UsageCaptureService usageCaptureService;

    public InferenceOrchestrator(BackendGateway backendGateway,
                                 ModelRoutingService modelRoutingService,
                                 ManagementService managementService,
                                 GatewayMetricsRecorder gatewayMetrics,
                                 AuditLogService auditLogService,
                                 UsageCaptureService usageCaptureService) {
        this.backendGateway = backendGateway;
        this.modelRoutingService = modelRoutingService;
        this.managementService = managementService;
        this.gatewayMetrics = gatewayMetrics;
        this.auditLogService = auditLogService;
        this.usageCaptureService = usageCaptureService;
    }

    public Response proxyChatCompletion(JsonNode requestBody,
                                        String customerId,
                                        String apiKeyId,
                                        String modelId,
                                        RequestContextFactory.RequestContext requestContext) {
        ManagementService.UsageScopeValidation validation = managementService.validateUsageScope(customerId, apiKeyId, modelId);
        if (!validation.valid()) {
            gatewayMetrics.recordApplicationError("scope_validation", modelId, String.valueOf(Response.Status.BAD_REQUEST.getStatusCode()));
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(validation.message())
                    .build();
        }

        String requestId = requestContext.requestId();
        String traceparent = requestContext.traceparent();
        Instant startedAt = requestContext.startedAt();

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

        List<ModelRoutingService.RouteTarget> routes = modelRoutingService.resolveCandidates(modelId);
        if (routes.isEmpty()) {
            gatewayMetrics.recordApplicationError("route_not_found", modelId, "503");
            return Response.status(Response.Status.SERVICE_UNAVAILABLE)
                    .entity("no active backend route for model: " + modelId)
                    .header("x-request-id", requestId)
                    .build();
        }

        Exception lastError = null;
        for (ModelRoutingService.RouteTarget route : routes) {
            try (Response backendResponse = backendGateway.chatCompletions(route.baseUrl(), requestId, traceparent, requestBody)) {
                JsonNode payload = backendResponse.readEntity(JsonNode.class);
                if (backendResponse.getStatus() >= 500) {
                    observeAndLog(startedAt, requestId, customerId, apiKeyId, modelId, backendResponse);
                    continue;
                }
                UsageCaptureService.EdgeUsageExtraction edgeUsageExtraction = UsageCaptureService.EdgeUsageExtraction.fromHeaders(
                        backendResponse.getHeaderString("x-edge-usage-prompt-tokens"),
                        backendResponse.getHeaderString("x-edge-usage-completion-tokens"),
                        backendResponse.getHeaderString("x-edge-usage-total-tokens")
                );
                UsageCaptureService.UsageCaptureDecision usageDecision = usageCaptureService.captureUsage(
                        customerId,
                        apiKeyId,
                        requestId,
                        payload,
                        edgeUsageExtraction
                );
                observeAndLog(startedAt, requestId, customerId, apiKeyId, modelId, backendResponse);

                Response.ResponseBuilder responseBuilder = Response.status(backendResponse.getStatus())
                        .entity(payload)
                        .header("x-grayfile-gateway", "grayfile-gateway")
                        .header("x-request-id", requestId)
                        .header("x-backend-id", route.backendId())
                        .header("x-grayfile-usage-capture", usageDecision.reason());

                if (usageDecision.divergenceDetected()) {
                    responseBuilder.header("x-grayfile-usage-divergence", "edge_backend_mismatch");
                }

                return responseBuilder.build();
            } catch (Exception exception) {
                lastError = exception;
            }
        }

        Exception exception = lastError == null ? new RuntimeException("all routes failed") : lastError;
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

    private void observeAndLog(Instant startedAt,
                               String requestId,
                               String customerId,
                               String apiKeyId,
                               String modelId,
                               Response backendResponse) {
        int backendStatus = backendResponse.getStatus();
        long latencyNanos = Duration.between(startedAt, Instant.now()).toNanos();
        gatewayMetrics.recordRequestLatency(modelId, customerId, apiKeyId, backendStatus, latencyNanos);

        String responseFlags = backendResponse.getHeaderString("x-envoy-response-flags");
        responseFlags = responseFlags == null ? "" : responseFlags;
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
}
