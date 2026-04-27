package io.grayfile.api;

import com.fasterxml.jackson.databind.JsonNode;
import io.grayfile.backend.OpenAiRequestContext;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.Optional;

@Path("/llm")
@Consumes(MediaType.APPLICATION_JSON)
public class LlmProxyResource {

    private final InferenceOrchestrator inferenceOrchestrator;
    private final RequestContextFactory requestContextFactory;

    public LlmProxyResource(InferenceOrchestrator inferenceOrchestrator,
                            RequestContextFactory requestContextFactory) {
        this.inferenceOrchestrator = inferenceOrchestrator;
        this.requestContextFactory = requestContextFactory;
    }

    @POST
    @Path("/v1/responses")
    public Response responses(JsonNode requestBody,
                              @HeaderParam("x-customer-id") String customerId,
                              @HeaderParam("x-api-key-id") String apiKeyId,
                              @Context HttpHeaders headers) {
        return handlePost(OpenAiEndpoint.RESPONSES, requestBody, customerId, apiKeyId, headers);
    }

    @POST
    @Path("/v1/chat/completions")
    public Response chatCompletions(JsonNode requestBody,
                                    @HeaderParam("x-customer-id") String customerId,
                                    @HeaderParam("x-api-key-id") String apiKeyId,
                                    @Context HttpHeaders headers) {
        return handlePost(OpenAiEndpoint.CHAT_COMPLETIONS, requestBody, customerId, apiKeyId, headers);
    }

    @POST
    @Path("/v1/completions")
    public Response completions(JsonNode requestBody,
                                @HeaderParam("x-customer-id") String customerId,
                                @HeaderParam("x-api-key-id") String apiKeyId,
                                @Context HttpHeaders headers) {
        return handlePost(OpenAiEndpoint.COMPLETIONS, requestBody, customerId, apiKeyId, headers);
    }

    @POST
    @Path("/v1/embeddings")
    public Response embeddings(JsonNode requestBody,
                               @HeaderParam("x-customer-id") String customerId,
                               @HeaderParam("x-api-key-id") String apiKeyId,
                               @Context HttpHeaders headers) {
        return handlePost(OpenAiEndpoint.EMBEDDINGS, requestBody, customerId, apiKeyId, headers);
    }

    @POST
    @Path("/v1/audio/transcriptions")
    public Response transcriptions(JsonNode requestBody,
                                   @HeaderParam("x-customer-id") String customerId,
                                   @HeaderParam("x-api-key-id") String apiKeyId,
                                   @Context HttpHeaders headers) {
        return handlePost(OpenAiEndpoint.AUDIO_TRANSCRIPTIONS, requestBody, customerId, apiKeyId, headers);
    }

    @POST
    @Path("/v1/audio/translations")
    public Response translations(JsonNode requestBody,
                                 @HeaderParam("x-customer-id") String customerId,
                                 @HeaderParam("x-api-key-id") String apiKeyId,
                                 @Context HttpHeaders headers) {
        return handlePost(OpenAiEndpoint.AUDIO_TRANSLATIONS, requestBody, customerId, apiKeyId, headers);
    }

    @POST
    @Path("/v1/audio/speech")
    public Response speech(JsonNode requestBody,
                           @HeaderParam("x-customer-id") String customerId,
                           @HeaderParam("x-api-key-id") String apiKeyId,
                           @Context HttpHeaders headers) {
        return handlePost(OpenAiEndpoint.AUDIO_SPEECH, requestBody, customerId, apiKeyId, headers);
    }

    @POST
    @Path("/v1/images/generations")
    public Response generations(JsonNode requestBody,
                                @HeaderParam("x-customer-id") String customerId,
                                @HeaderParam("x-api-key-id") String apiKeyId,
                                @Context HttpHeaders headers) {
        return handlePost(OpenAiEndpoint.IMAGE_GENERATIONS, requestBody, customerId, apiKeyId, headers);
    }

    @POST
    @Path("/v1/moderations")
    public Response moderations(JsonNode requestBody,
                                @HeaderParam("x-customer-id") String customerId,
                                @HeaderParam("x-api-key-id") String apiKeyId,
                                @Context HttpHeaders headers) {
        return handlePost(OpenAiEndpoint.MODERATIONS, requestBody, customerId, apiKeyId, headers);
    }

    @GET
    @Path("/v1/models")
    public Response models(@HeaderParam("x-customer-id") String customerId,
                           @HeaderParam("x-api-key-id") String apiKeyId,
                           @Context HttpHeaders headers) {
        return handleGet(OpenAiEndpoint.MODELS, customerId, apiKeyId, headers);
    }

    @GET
    @Path("/v1/files")
    public Response files(@HeaderParam("x-customer-id") String customerId,
                          @HeaderParam("x-api-key-id") String apiKeyId,
                          @Context HttpHeaders headers) {
        return handleGet(OpenAiEndpoint.FILES, customerId, apiKeyId, headers);
    }

    @POST
    @Path("/v1/files")
    public Response filesCreate(JsonNode requestBody,
                                @HeaderParam("x-customer-id") String customerId,
                                @HeaderParam("x-api-key-id") String apiKeyId,
                                @Context HttpHeaders headers) {
        return handlePost(OpenAiEndpoint.FILES, requestBody, customerId, apiKeyId, headers);
    }

    private Response handlePost(OpenAiEndpoint endpoint,
                                JsonNode requestBody,
                                String customerId,
                                String apiKeyId,
                                HttpHeaders headers) {
        if (requestBody == null || !requestBody.isObject()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("request body must be a JSON object")
                    .build();
        }
        return proxy(endpoint, "POST", requestBody, customerId, apiKeyId, headers);
    }

    private Response handleGet(OpenAiEndpoint endpoint,
                               String customerId,
                               String apiKeyId,
                               HttpHeaders headers) {
        return proxy(endpoint, "GET", null, customerId, apiKeyId, headers);
    }

    private Response proxy(OpenAiEndpoint endpoint,
                           String httpMethod,
                           JsonNode requestBody,
                           String customerId,
                           String apiKeyId,
                           HttpHeaders headers) {
        if (!endpoint.allowsMethod(httpMethod)) {
            return Response.status(Response.Status.METHOD_NOT_ALLOWED).build();
        }
        if (customerId == null || customerId.isBlank() || apiKeyId == null || apiKeyId.isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("x-customer-id and x-api-key-id headers are required")
                    .build();
        }

        if (!endpoint.requiresModel()) {
            return Response.status(Response.Status.NOT_IMPLEMENTED)
                    .entity("endpoint " + endpoint.path() + " is registered but disabled until a model-agnostic routing policy is configured")
                    .build();
        }

        String modelId = null;
        if (endpoint.requiresModel()) {
            modelId = Optional.ofNullable(requestBody)
                    .map(body -> body.path("model").asText(null))
                    .filter(model -> !model.isBlank())
                    .orElse(null);
            if (modelId == null) {
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity("request body must contain a non-empty model")
                        .build();
            }
        }

        RequestContextFactory.RequestContext context = requestContextFactory.create(headers);
        OpenAiRequestContext proxyContext = new OpenAiRequestContext(
                endpoint,
                httpMethod,
                customerId,
                apiKeyId,
                modelId,
                requestBody,
                context.requestId(),
                context.traceparent(),
                context.startedAt()
        );
        return inferenceOrchestrator.proxy(proxyContext);
    }
}
