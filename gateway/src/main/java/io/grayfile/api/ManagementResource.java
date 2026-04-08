package io.grayfile.api;

import io.grayfile.domain.ApiKeyEntity;
import io.grayfile.domain.CustomerEntity;
import io.grayfile.domain.LlmModelEntity;
import io.grayfile.service.ManagementService;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

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
}
