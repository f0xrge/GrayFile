package io.grayfile.api;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.Optional;

@Path("/llm/v1")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class LlmProxyResource {

    private final InferenceOrchestrator inferenceOrchestrator;
    private final RequestContextFactory requestContextFactory;

    public LlmProxyResource(InferenceOrchestrator inferenceOrchestrator,
                            RequestContextFactory requestContextFactory) {
        this.inferenceOrchestrator = inferenceOrchestrator;
        this.requestContextFactory = requestContextFactory;
    }

    @POST
    @Path("/chat/completions")
    public Response createCompletion(JsonNode requestBody,
                                     @HeaderParam("x-customer-id") String customerId,
                                     @HeaderParam("x-api-key-id") String apiKeyId,
                                     @Context HttpHeaders headers) {
        if (requestBody == null || !requestBody.isObject()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("request body must be a JSON object")
                    .build();
        }
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

        return inferenceOrchestrator.proxyChatCompletion(
                requestBody,
                customerId,
                apiKeyId,
                modelId,
                requestContextFactory.create(headers)
        );
    }
}
