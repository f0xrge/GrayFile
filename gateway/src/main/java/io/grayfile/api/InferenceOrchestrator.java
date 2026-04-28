package io.grayfile.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.grayfile.backend.BackendGateway;
import io.grayfile.backend.OpenAiRequestContext;
import io.grayfile.api.usage.EndpointBillingPolicy;
import io.grayfile.metrics.GatewayMetricsRecorder;
import io.grayfile.service.AuditLogService;
import io.grayfile.service.ManagementService;
import io.grayfile.service.ModelRoutingService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.ProcessingException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.jboss.logging.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
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
    private final ObjectMapper objectMapper;
    private final EndpointBillingPolicy endpointBillingPolicy;

    public InferenceOrchestrator(BackendGateway backendGateway,
                                 ModelRoutingService modelRoutingService,
                                 ManagementService managementService,
                                 GatewayMetricsRecorder gatewayMetrics,
                                 AuditLogService auditLogService,
                                 UsageCaptureService usageCaptureService,
                                 ObjectMapper objectMapper,
                                 EndpointBillingPolicy endpointBillingPolicy) {
        this.backendGateway = backendGateway;
        this.modelRoutingService = modelRoutingService;
        this.managementService = managementService;
        this.gatewayMetrics = gatewayMetrics;
        this.auditLogService = auditLogService;
        this.usageCaptureService = usageCaptureService;
        this.objectMapper = objectMapper;
        this.endpointBillingPolicy = endpointBillingPolicy;
    }

    public Response proxy(OpenAiRequestContext requestContext) {
        OpenAiEndpoint endpoint = requestContext.endpoint();
        String modelId = requestContext.modelId();

        boolean billableEndpoint = endpointBillingPolicy.isBillable(endpoint);

        ManagementService.UsageScopeValidation validation = billableEndpoint
                ? managementService.validateUsageScope(requestContext.customerId(), requestContext.apiKeyId(), modelId)
                : managementService.validateCustomerApiScope(requestContext.customerId(), requestContext.apiKeyId());
        if (!validation.valid()) {
            gatewayMetrics.recordApplicationError("scope_validation", modelId == null ? "none" : modelId,
                    String.valueOf(Response.Status.BAD_REQUEST.getStatusCode()));
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(validation.message())
                    .build();
        }

        String requestId = requestContext.requestId();
        Instant startedAt = requestContext.startedAt();

        auditLogService.logEvent(
                "MODEL_ROUTING_DECISION",
                "gateway-router",
                "routing",
                requestId,
                auditLogService.payloadOf(
                        "customer_id", requestContext.customerId(),
                        "api_key_id", requestContext.apiKeyId(),
                        "model", modelId,
                        "endpoint", endpoint.path(),
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
            try (Response backendResponse = backendGateway.proxy(route.baseUrl(), requestContext)) {
                ResponseEnvelope envelope = ResponseEnvelope.from(backendResponse, objectMapper);

                if (backendResponse.getStatus() >= 500) {
                    observeAndLog(startedAt, requestId, requestContext.customerId(), requestContext.apiKeyId(), modelId, backendResponse);
                    continue;
                }

                UsageCaptureService.UsageCaptureDecision usageDecision = UsageCaptureService.UsageCaptureDecision.skippedReason("not_billable_endpoint");
                if (billableEndpoint) {
                    boolean streamingRequest = isStreamingRequest(requestContext);
                    UsageCaptureService.EdgeUsageExtraction edgeUsageExtraction = UsageCaptureService.EdgeUsageExtraction.fromHeaders(
                            backendResponse.getHeaderString("x-edge-usage-prompt-tokens"),
                            backendResponse.getHeaderString("x-edge-usage-completion-tokens"),
                            backendResponse.getHeaderString("x-edge-usage-total-tokens")
                    );
                    long usageDurationMs = Duration.between(startedAt, Instant.now()).toMillis();

                    if (streamingRequest) {
                        if (edgeUsageExtraction.present()) {
                            usageDecision = usageCaptureService.captureUsage(
                                    requestContext.customerId(),
                                    requestContext.apiKeyId(),
                                    requestId,
                                    usageDurationMs,
                                    endpoint,
                                    synthesizeStreamingPayload(modelId, edgeUsageExtraction),
                                    edgeUsageExtraction
                            );
                        } else {
                            usageDecision = UsageCaptureService.UsageCaptureDecision.skippedReason("stream_final_missing_usage");
                            usageCaptureService.auditSkippedExtraction("stream_final_missing_usage", requestId, modelId, endpoint);
                        }
                    } else if (envelope.jsonPayload() != null) {
                        usageDecision = usageCaptureService.captureUsage(
                                requestContext.customerId(),
                                requestContext.apiKeyId(),
                                requestId,
                                usageDurationMs,
                                endpoint,
                                envelope.jsonPayload(),
                                edgeUsageExtraction
                        );
                    } else {
                        usageDecision = UsageCaptureService.UsageCaptureDecision.skippedReason("incomplete_stream");
                        usageCaptureService.auditSkippedExtraction("incomplete_stream", requestId, modelId, endpoint);
                    }

                    if (!usageDecision.captured()) {
                        gatewayMetrics.recordUsageExtractionError(usageDecision.reason(), modelId);
                    }
                }

                observeAndLog(startedAt, requestId, requestContext.customerId(), requestContext.apiKeyId(), modelId, backendResponse);

                Response.ResponseBuilder responseBuilder = Response.status(backendResponse.getStatus())
                        .entity(envelope.rawPayload())
                        .header("Content-Type", envelope.contentType())
                        .header("x-grayfile-gateway", "grayfile-gateway")
                        .header("x-request-id", requestId)
                        .header("x-backend-id", route.backendId());

                if (billableEndpoint) {
                    responseBuilder.header("x-grayfile-usage-capture", usageDecision.reason());
                    if (usageDecision.contractVersion() != null) {
                        responseBuilder.header("x-grayfile-usage-contract-version", usageDecision.contractVersion());
                    }
                    if (usageDecision.extractorVersion() != null) {
                        responseBuilder.header("x-grayfile-usage-extractor-version", usageDecision.extractorVersion());
                    }
                    if (usageDecision.divergenceDetected()) {
                        responseBuilder.header("x-grayfile-usage-divergence", "edge_backend_mismatch");
                    }
                }

                return responseBuilder.build();
            } catch (Exception exception) {
                lastError = exception;
            }
        }

        Exception exception = lastError == null ? new RuntimeException("all routes failed") : lastError;
        long latencyNanos = Duration.between(startedAt, Instant.now()).toNanos();
        gatewayMetrics.recordRequestLatency(modelId, requestContext.customerId(), requestContext.apiKeyId(), 500, latencyNanos);
        gatewayMetrics.recordApplicationError("gateway_exception", modelId, "500");
        LOG.errorf(exception,
                "{\"event\":\"gateway_request\",\"request_id\":\"%s\",\"customer_id\":\"%s\",\"api_key_id\":\"%s\",\"model\":\"%s\",\"endpoint\":\"%s\",\"backend_status\":500,\"latency_ms\":%d}",
                requestId,
                requestContext.customerId(),
                requestContext.apiKeyId(),
                modelId,
                endpoint.path(),
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

    private boolean isStreamingRequest(OpenAiRequestContext requestContext) {
        JsonNode requestBody = requestContext.requestBody();
        return requestBody != null && requestBody.path("stream").asBoolean(false);
    }

    private JsonNode synthesizeStreamingPayload(String modelId, UsageCaptureService.EdgeUsageExtraction edgeUsageExtraction) {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("model", modelId == null ? "unknown-model" : modelId);
        ObjectNode usage = payload.putObject("usage");
        usage.put("prompt_tokens", edgeUsageExtraction.inputTokens());
        usage.put("completion_tokens", edgeUsageExtraction.outputTokens());
        usage.put("total_tokens", edgeUsageExtraction.totalTokens());
        return payload;
    }

    private record ResponseEnvelope(byte[] rawPayload, String contentType, JsonNode jsonPayload) {

        static ResponseEnvelope from(Response backendResponse, ObjectMapper objectMapper) {
            byte[] body = readBody(backendResponse, objectMapper);
            String contentType = MediaType.APPLICATION_JSON;
            MediaType mediaType = backendResponse.getMediaType();
            if (mediaType != null) {
                contentType = mediaType.toString();
            }

            JsonNode json = null;
            if (contentType.toLowerCase().contains("json") && body.length > 0) {
                try {
                    json = objectMapper.readTree(body);
                } catch (IOException ignored) {
                    json = null;
                }
            }
            return new ResponseEnvelope(body, contentType, json);
        }

        private static byte[] readBody(Response backendResponse, ObjectMapper objectMapper) {
            if (!backendResponse.hasEntity()) {
                return new byte[0];
            }

            Object entity = backendResponse.getEntity();
            if (entity instanceof byte[] bytes) {
                return bytes;
            }
            if (entity instanceof String text) {
                return text.getBytes(StandardCharsets.UTF_8);
            }
            if (entity instanceof JsonNode jsonNode) {
                try {
                    return objectMapper.writeValueAsBytes(jsonNode);
                } catch (IOException ignored) {
                    return new byte[0];
                }
            }
            if (entity != null) {
                try {
                    return objectMapper.writeValueAsBytes(entity);
                } catch (IOException ignored) {
                    return new byte[0];
                }
            }

            try {
                return backendResponse.readEntity(byte[].class);
            } catch (ProcessingException ignored) {
                return new byte[0];
            }
        }
    }
}
