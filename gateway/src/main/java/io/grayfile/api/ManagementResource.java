package io.grayfile.api;

import io.grayfile.domain.ApiKeyEntity;
import io.grayfile.domain.BillingWindowEntity;
import io.grayfile.domain.CustomerEntity;
import io.grayfile.domain.LlmModelEntity;
import io.grayfile.service.ManagementService;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.List;

@Path("/management/v1")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class ManagementResource {

    private final ManagementService managementService;

    public ManagementResource(ManagementService managementService) {
        this.managementService = managementService;
    }

    @GET
    @Path("/customers")
    public List<CustomerResponse> listCustomers() {
        return managementService.listCustomers().stream().map(CustomerResponse::from).toList();
    }

    @GET
    @Path("/customers/{customerId}")
    public CustomerResponse getCustomer(@PathParam("customerId") String customerId) {
        return CustomerResponse.from(managementService.getCustomer(customerId));
    }

    @POST
    @Path("/customers")
    public Response createCustomer(CustomerUpsertRequest request) {
        CustomerEntity entity = managementService.createCustomer(request.id(), request.name(), request.active());
        return Response.status(Response.Status.CREATED).entity(CustomerResponse.from(entity)).build();
    }

    @PUT
    @Path("/customers/{customerId}")
    public CustomerResponse updateCustomer(@PathParam("customerId") String customerId, CustomerUpdateRequest request) {
        return CustomerResponse.from(managementService.updateCustomer(customerId, request.name(), request.active()));
    }

    @DELETE
    @Path("/customers/{customerId}")
    public CustomerResponse deactivateCustomer(@PathParam("customerId") String customerId) {
        return CustomerResponse.from(managementService.deactivateCustomer(customerId));
    }

    @GET
    @Path("/models")
    public List<ModelResponse> listModels() {
        return managementService.listModels().stream().map(ModelResponse::from).toList();
    }

    @GET
    @Path("/models/{modelId}")
    public ModelResponse getModel(@PathParam("modelId") String modelId) {
        return ModelResponse.from(managementService.getModel(modelId));
    }

    @POST
    @Path("/models")
    public Response createModel(ModelUpsertRequest request) {
        LlmModelEntity entity = managementService.createModel(
                request.id(),
                request.displayName(),
                request.provider(),
                request.active()
        );
        return Response.status(Response.Status.CREATED).entity(ModelResponse.from(entity)).build();
    }

    @PUT
    @Path("/models/{modelId}")
    public ModelResponse updateModel(@PathParam("modelId") String modelId, ModelUpdateRequest request) {
        return ModelResponse.from(managementService.updateModel(
                modelId,
                request.displayName(),
                request.provider(),
                request.active()
        ));
    }

    @DELETE
    @Path("/models/{modelId}")
    public ModelResponse deactivateModel(@PathParam("modelId") String modelId) {
        return ModelResponse.from(managementService.deactivateModel(modelId));
    }

    @GET
    @Path("/api-keys")
    public List<ApiKeyResponse> listApiKeys() {
        return managementService.listApiKeys().stream().map(ApiKeyResponse::from).toList();
    }

    @GET
    @Path("/api-keys/{apiKeyId}")
    public ApiKeyResponse getApiKey(@PathParam("apiKeyId") String apiKeyId) {
        return ApiKeyResponse.from(managementService.getApiKey(apiKeyId));
    }

    @POST
    @Path("/api-keys")
    public Response createApiKey(ApiKeyCreateRequest request) {
        ApiKeyEntity entity = managementService.createApiKey(
                request.id(),
                request.customerId(),
                request.name(),
                request.active()
        );
        return Response.status(Response.Status.CREATED).entity(ApiKeyResponse.from(entity)).build();
    }

    @PUT
    @Path("/api-keys/{apiKeyId}")
    public ApiKeyResponse updateApiKey(@PathParam("apiKeyId") String apiKeyId, ApiKeyUpdateRequest request) {
        return ApiKeyResponse.from(managementService.updateApiKey(apiKeyId, request.name(), request.active()));
    }

    @DELETE
    @Path("/api-keys/{apiKeyId}")
    public ApiKeyResponse deactivateApiKey(@PathParam("apiKeyId") String apiKeyId) {
        return ApiKeyResponse.from(managementService.deactivateApiKey(apiKeyId));
    }

    @GET
    @Path("/billing-windows")
    public List<BillingWindowResponse> listBillingWindows(@QueryParam("customerId") String customerId,
                                                          @QueryParam("apiKeyId") String apiKeyId,
                                                          @QueryParam("startDate") String startDate,
                                                          @QueryParam("endDate") String endDate) {
        return managementService.listBillingWindows(
                        customerId,
                        apiKeyId,
                        parseInstant(startDate, "startDate"),
                        parseInstant(endDate, "endDate")
                )
                .stream()
                .map(BillingWindowResponse::from)
                .toList();
    }

    public record CustomerUpsertRequest(String id, String name, Boolean active) {
    }

    public record CustomerUpdateRequest(String name, Boolean active) {
    }

    public record ModelUpsertRequest(String id, String displayName, String provider, Boolean active) {
    }

    public record ModelUpdateRequest(String displayName, String provider, Boolean active) {
    }

    public record ApiKeyCreateRequest(String id, String customerId, String name, Boolean active) {
    }

    public record ApiKeyUpdateRequest(String name, Boolean active) {
    }

    public record CustomerResponse(String id, String name, boolean active) {
        static CustomerResponse from(CustomerEntity entity) {
            return new CustomerResponse(entity.id, entity.name, entity.active);
        }
    }

    public record ModelResponse(String id, String displayName, String provider, boolean active) {
        static ModelResponse from(LlmModelEntity entity) {
            return new ModelResponse(entity.id, entity.displayName, entity.provider, entity.active);
        }
    }

    public record ApiKeyResponse(String id, String customerId, String name, boolean active) {
        static ApiKeyResponse from(ApiKeyEntity entity) {
            return new ApiKeyResponse(entity.id, entity.customerId, entity.name, entity.active);
        }
    }

    public record BillingWindowResponse(String id,
                                        String customerId,
                                        String apiKeyId,
                                        String model,
                                        Instant windowStart,
                                        Instant windowEnd,
                                        int tokenTotal,
                                        String closureReason,
                                        boolean active) {
        static BillingWindowResponse from(BillingWindowEntity entity) {
            return new BillingWindowResponse(
                    entity.id.toString(),
                    entity.customerId,
                    entity.apiKeyId,
                    entity.model,
                    entity.windowStart,
                    entity.windowEnd,
                    entity.tokenTotal,
                    entity.closureReason,
                    entity.active
            );
        }
    }

    private Instant parseInstant(String value, String label) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(value.trim());
        } catch (DateTimeParseException exception) {
            throw new BadRequestException(label + " must be an ISO-8601 instant");
        }
    }
}
