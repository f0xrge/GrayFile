package io.grayfile.api;

import com.fasterxml.jackson.databind.JsonNode;
import io.grayfile.backend.BackendClient;
import io.grayfile.billing.BillingService;
import io.grayfile.service.ManagementService;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.rest.client.inject.RestClient;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Path("/llm/v1")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class LlmProxyResource {

    private final BackendClient backendClient;
    private final BillingService billingService;
    private final ManagementService managementService;

    public LlmProxyResource(@RestClient BackendClient backendClient,
                            BillingService billingService,
                            ManagementService managementService) {
        this.backendClient = backendClient;
        this.billingService = billingService;
        this.managementService = managementService;
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
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(validation.message())
                    .build();
        }

        try (Response backendResponse = backendClient.chatCompletions(requestBody)) {
            JsonNode payload = backendResponse.readEntity(JsonNode.class);
            captureUsage(customerId, apiKeyId, headers, payload);
            return Response.status(backendResponse.getStatus())
                    .entity(payload)
                    .header("x-grayfile-gateway", "grayfile-gateway")
                    .build();
        }
    }

    private void captureUsage(String customerId, String apiKeyId, HttpHeaders headers, JsonNode payload) {
        JsonNode usageNode = payload.path("usage");
        if (usageNode.isMissingNode()) {
            return;
        }

        String requestId = Optional.ofNullable(headers.getHeaderString("x-request-id"))
                .filter(s -> !s.isBlank())
                .orElseGet(() -> payload.path("id").asText("req_" + UUID.randomUUID()));

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
}
