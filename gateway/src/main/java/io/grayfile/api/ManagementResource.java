package io.grayfile.api;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.grayfile.domain.ApiKeyEntity;
import io.grayfile.domain.AuditLogEntity;
import io.grayfile.domain.BillingWindowEntity;
import io.grayfile.domain.CustomerEntity;
import io.grayfile.domain.CustomerModelPricingEntity;
import io.grayfile.domain.LlmModelEntity;
import io.grayfile.domain.ModelRouteEntity;
import io.grayfile.service.ManagementService;
import io.grayfile.service.UsageAnalyticsService;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Path("/management/v1")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class ManagementResource {

    private final ManagementService managementService;
    private final ObjectMapper objectMapper;

    public ManagementResource(ManagementService managementService, ObjectMapper objectMapper) {
        this.managementService = managementService;
        this.objectMapper = objectMapper;
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
    public Response createCustomer(CustomerUpsertRequest request,
                                   @HeaderParam("x-actor-id") String actorId,
                                   @HeaderParam("x-source-ip") String sourceIp,
                                   @HeaderParam("x-request-id") String requestId,
                                   @HeaderParam("x-change-reason") String reason,
                                   @HeaderParam("x-second-approver-id") String secondApproverId,
                                   @HeaderParam("x-bulk-change-size") Integer bulkChangeSize) {
        CustomerEntity entity = managementService.createCustomer(
                request.id(),
                request.name(),
                request.active(),
                auditContext(actorId, sourceIp, requestId, reason, request.changeType(), secondApproverId, bulkChangeSize)
        );
        return Response.status(Response.Status.CREATED).entity(CustomerResponse.from(entity)).build();
    }

    @PUT
    @Path("/customers/{customerId}")
    public CustomerResponse updateCustomer(@PathParam("customerId") String customerId,
                                           CustomerUpdateRequest request,
                                           @HeaderParam("x-actor-id") String actorId,
                                           @HeaderParam("x-source-ip") String sourceIp,
                                           @HeaderParam("x-request-id") String requestId,
                                           @HeaderParam("x-change-reason") String reason,
                                           @HeaderParam("x-second-approver-id") String secondApproverId,
                                           @HeaderParam("x-bulk-change-size") Integer bulkChangeSize) {
        return CustomerResponse.from(managementService.updateCustomer(
                customerId,
                request.name(),
                request.active(),
                auditContext(actorId, sourceIp, requestId, reason, request.changeType(), secondApproverId, bulkChangeSize)
        ));
    }

    @DELETE
    @Path("/customers/{customerId}")
    public CustomerResponse deactivateCustomer(@PathParam("customerId") String customerId,
                                               @HeaderParam("x-actor-id") String actorId,
                                               @HeaderParam("x-source-ip") String sourceIp,
                                               @HeaderParam("x-request-id") String requestId,
                                               @HeaderParam("x-change-reason") String reason,
                                               @HeaderParam("x-second-approver-id") String secondApproverId,
                                               @HeaderParam("x-bulk-change-size") Integer bulkChangeSize,
                                               @QueryParam("changeType") String changeType) {
        return CustomerResponse.from(managementService.deactivateCustomer(
                customerId,
                auditContext(actorId, sourceIp, requestId, reason, changeType, secondApproverId, bulkChangeSize)
        ));
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
    public Response createModel(ModelUpsertRequest request,
                                @HeaderParam("x-actor-id") String actorId,
                                @HeaderParam("x-source-ip") String sourceIp,
                                @HeaderParam("x-request-id") String requestId,
                                @HeaderParam("x-change-reason") String reason,
                                @HeaderParam("x-second-approver-id") String secondApproverId,
                                @HeaderParam("x-bulk-change-size") Integer bulkChangeSize) {
        LlmModelEntity entity = managementService.createModel(
                request.id(),
                request.displayName(),
                request.provider(),
                request.active(),
                request.defaultTimeCriterionSeconds(),
                request.defaultTimePrice(),
                request.defaultTokenCriterion(),
                request.defaultTokenPrice(),
                auditContext(actorId, sourceIp, requestId, reason, request.changeType(), secondApproverId, bulkChangeSize)
        );
        return Response.status(Response.Status.CREATED).entity(ModelResponse.from(entity)).build();
    }

    @PUT
    @Path("/models/{modelId}")
    public ModelResponse updateModel(@PathParam("modelId") String modelId,
                                     ModelUpdateRequest request,
                                     @HeaderParam("x-actor-id") String actorId,
                                     @HeaderParam("x-source-ip") String sourceIp,
                                     @HeaderParam("x-request-id") String requestId,
                                     @HeaderParam("x-change-reason") String reason,
                                     @HeaderParam("x-second-approver-id") String secondApproverId,
                                     @HeaderParam("x-bulk-change-size") Integer bulkChangeSize) {
        return ModelResponse.from(managementService.updateModel(
                modelId,
                request.displayName(),
                request.provider(),
                request.active(),
                request.defaultTimeCriterionSeconds(),
                request.defaultTimePrice(),
                request.defaultTokenCriterion(),
                request.defaultTokenPrice(),
                auditContext(actorId, sourceIp, requestId, reason, request.changeType(), secondApproverId, bulkChangeSize)
        ));
    }

    @DELETE
    @Path("/models/{modelId}")
    public ModelResponse deactivateModel(@PathParam("modelId") String modelId,
                                         @HeaderParam("x-actor-id") String actorId,
                                         @HeaderParam("x-source-ip") String sourceIp,
                                         @HeaderParam("x-request-id") String requestId,
                                         @HeaderParam("x-change-reason") String reason,
                                         @HeaderParam("x-second-approver-id") String secondApproverId,
                                         @HeaderParam("x-bulk-change-size") Integer bulkChangeSize,
                                         @QueryParam("changeType") String changeType) {
        return ModelResponse.from(managementService.deactivateModel(
                modelId,
                auditContext(actorId, sourceIp, requestId, reason, changeType, secondApproverId, bulkChangeSize)
        ));
    }


    @GET
    @Path("/models/{modelId}/routes")
    public List<ModelRouteResponse> listModelRoutes(@PathParam("modelId") String modelId) {
        return managementService.listModelRoutes(modelId).stream().map(ModelRouteResponse::from).toList();
    }

    @POST
    @Path("/models/{modelId}/routes")
    public Response createModelRoute(@PathParam("modelId") String modelId,
                                     ModelRouteUpsertRequest request,
                                     @HeaderParam("x-actor-id") String actorId,
                                     @HeaderParam("x-source-ip") String sourceIp,
                                     @HeaderParam("x-request-id") String requestId,
                                     @HeaderParam("x-change-reason") String reason,
                                     @HeaderParam("x-second-approver-id") String secondApproverId,
                                     @HeaderParam("x-bulk-change-size") Integer bulkChangeSize) {
        ModelRouteEntity entity = managementService.createModelRoute(
                modelId,
                request.backendId(),
                request.baseUrl(),
                request.weight(),
                request.active(),
                auditContext(actorId, sourceIp, requestId, reason, request.changeType(), secondApproverId, bulkChangeSize)
        );
        return Response.status(Response.Status.CREATED).entity(ModelRouteResponse.from(entity)).build();
    }

    @PUT
    @Path("/models/{modelId}/routes/{backendId}/active")
    public ModelRouteResponse setModelRouteActive(@PathParam("modelId") String modelId,
                                                  @PathParam("backendId") String backendId,
                                                  ModelRouteActiveRequest request,
                                                  @HeaderParam("x-actor-id") String actorId,
                                                  @HeaderParam("x-source-ip") String sourceIp,
                                                  @HeaderParam("x-request-id") String requestId,
                                                  @HeaderParam("x-change-reason") String reason,
                                                  @HeaderParam("x-second-approver-id") String secondApproverId,
                                                  @HeaderParam("x-bulk-change-size") Integer bulkChangeSize) {
        return ModelRouteResponse.from(managementService.setModelRouteActive(
                modelId,
                backendId,
                request.active(),
                auditContext(actorId, sourceIp, requestId, reason, request.changeType(), secondApproverId, bulkChangeSize)
        ));
    }

    @PUT
    @Path("/models/{modelId}/routes/{backendId}/weight")
    public ModelRouteResponse setModelRouteWeight(@PathParam("modelId") String modelId,
                                                  @PathParam("backendId") String backendId,
                                                  ModelRouteWeightRequest request,
                                                  @HeaderParam("x-actor-id") String actorId,
                                                  @HeaderParam("x-source-ip") String sourceIp,
                                                  @HeaderParam("x-request-id") String requestId,
                                                  @HeaderParam("x-change-reason") String reason,
                                                  @HeaderParam("x-second-approver-id") String secondApproverId,
                                                  @HeaderParam("x-bulk-change-size") Integer bulkChangeSize) {
        return ModelRouteResponse.from(managementService.setModelRouteWeight(
                modelId,
                backendId,
                request.weight(),
                auditContext(actorId, sourceIp, requestId, reason, request.changeType(), secondApproverId, bulkChangeSize)
        ));
    }

    @DELETE
    @Path("/models/{modelId}/routes/{backendId}")
    public Response deleteModelRoute(@PathParam("modelId") String modelId,
                                     @PathParam("backendId") String backendId,
                                     @HeaderParam("x-actor-id") String actorId,
                                     @HeaderParam("x-source-ip") String sourceIp,
                                     @HeaderParam("x-request-id") String requestId,
                                     @HeaderParam("x-change-reason") String reason,
                                     @HeaderParam("x-second-approver-id") String secondApproverId,
                                     @HeaderParam("x-bulk-change-size") Integer bulkChangeSize,
                                     @QueryParam("changeType") String changeType) {
        managementService.deleteModelRoute(
                modelId,
                backendId,
                auditContext(actorId, sourceIp, requestId, reason, changeType, secondApproverId, bulkChangeSize)
        );
        return Response.noContent().build();
    }

    @GET
    @Path("/models/{modelId}/customer-pricing")
    public List<CustomerModelPricingResponse> listCustomerPricing(@PathParam("modelId") String modelId) {
        return managementService.listCustomerPricingForModel(modelId).stream().map(CustomerModelPricingResponse::from).toList();
    }

    @PUT
    @Path("/models/{modelId}/customer-pricing/{customerId}")
    public CustomerModelPricingResponse upsertCustomerPricing(@PathParam("modelId") String modelId,
                                                              @PathParam("customerId") String customerId,
                                                              CustomerModelPricingUpsertRequest request,
                                                              @HeaderParam("x-actor-id") String actorId,
                                                              @HeaderParam("x-source-ip") String sourceIp,
                                                              @HeaderParam("x-request-id") String requestId,
                                                              @HeaderParam("x-change-reason") String reason,
                                                              @HeaderParam("x-second-approver-id") String secondApproverId,
                                                              @HeaderParam("x-bulk-change-size") Integer bulkChangeSize) {
        return CustomerModelPricingResponse.from(managementService.upsertCustomerPricing(
                modelId,
                customerId,
                request.timeCriterionSeconds(),
                request.timePrice(),
                request.tokenCriterion(),
                request.tokenPrice(),
                auditContext(actorId, sourceIp, requestId, reason, request.changeType(), secondApproverId, bulkChangeSize)
        ));
    }

    @DELETE
    @Path("/models/{modelId}/customer-pricing/{customerId}")
    public Response deleteCustomerPricing(@PathParam("modelId") String modelId,
                                          @PathParam("customerId") String customerId,
                                          @HeaderParam("x-actor-id") String actorId,
                                          @HeaderParam("x-source-ip") String sourceIp,
                                          @HeaderParam("x-request-id") String requestId,
                                          @HeaderParam("x-change-reason") String reason,
                                          @HeaderParam("x-second-approver-id") String secondApproverId,
                                          @HeaderParam("x-bulk-change-size") Integer bulkChangeSize,
                                          @QueryParam("changeType") String changeType) {
        managementService.deleteCustomerPricing(
                modelId,
                customerId,
                auditContext(actorId, sourceIp, requestId, reason, changeType, secondApproverId, bulkChangeSize)
        );
        return Response.noContent().build();
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
    public Response createApiKey(ApiKeyCreateRequest request,
                                 @HeaderParam("x-actor-id") String actorId,
                                 @HeaderParam("x-source-ip") String sourceIp,
                                 @HeaderParam("x-request-id") String requestId,
                                 @HeaderParam("x-change-reason") String reason,
                                 @HeaderParam("x-second-approver-id") String secondApproverId,
                                 @HeaderParam("x-bulk-change-size") Integer bulkChangeSize) {
        ApiKeyEntity entity = managementService.createApiKey(
                request.id(),
                request.customerId(),
                request.name(),
                request.active(),
                auditContext(actorId, sourceIp, requestId, reason, request.changeType(), secondApproverId, bulkChangeSize)
        );
        return Response.status(Response.Status.CREATED).entity(ApiKeyResponse.from(entity)).build();
    }

    @PUT
    @Path("/api-keys/{apiKeyId}")
    public ApiKeyResponse updateApiKey(@PathParam("apiKeyId") String apiKeyId,
                                       ApiKeyUpdateRequest request,
                                       @HeaderParam("x-actor-id") String actorId,
                                       @HeaderParam("x-source-ip") String sourceIp,
                                       @HeaderParam("x-request-id") String requestId,
                                       @HeaderParam("x-change-reason") String reason,
                                       @HeaderParam("x-second-approver-id") String secondApproverId,
                                       @HeaderParam("x-bulk-change-size") Integer bulkChangeSize) {
        return ApiKeyResponse.from(managementService.updateApiKey(
                apiKeyId,
                request.name(),
                request.active(),
                auditContext(actorId, sourceIp, requestId, reason, request.changeType(), secondApproverId, bulkChangeSize)
        ));
    }

    @DELETE
    @Path("/api-keys/{apiKeyId}")
    public ApiKeyResponse deactivateApiKey(@PathParam("apiKeyId") String apiKeyId,
                                           @HeaderParam("x-actor-id") String actorId,
                                           @HeaderParam("x-source-ip") String sourceIp,
                                           @HeaderParam("x-request-id") String requestId,
                                           @HeaderParam("x-change-reason") String reason,
                                           @HeaderParam("x-second-approver-id") String secondApproverId,
                                           @HeaderParam("x-bulk-change-size") Integer bulkChangeSize,
                                           @QueryParam("changeType") String changeType) {
        return ApiKeyResponse.from(managementService.deactivateApiKey(
                apiKeyId,
                auditContext(actorId, sourceIp, requestId, reason, changeType, secondApproverId, bulkChangeSize)
        ));
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

    @GET
    @Path("/audit-events")
    public List<AuditEventResponse> listAuditEvents(@QueryParam("type") String eventType,
                                                    @QueryParam("startDate") String startDate,
                                                    @QueryParam("endDate") String endDate,
                                                    @QueryParam("entityType") String entityType,
                                                    @QueryParam("entityId") String entityId,
                                                    @QueryParam("limit") Integer limit) {
        int resolvedLimit = limit == null ? 100 : limit;
        return managementService.listAuditEvents(
                        eventType,
                        parseInstant(startDate, "startDate"),
                        parseInstant(endDate, "endDate"),
                        entityType,
                        entityId,
                        resolvedLimit
                )
                .stream()
                .map(this::toAuditEventResponse)
                .toList();
    }

    @GET
    @Path("/usage-analytics")
    public UsageAnalyticsService.UsageAnalyticsResponse getUsageAnalytics(@QueryParam("customerId") String customerId,
                                                                          @QueryParam("modelId") String modelId,
                                                                          @QueryParam("startDate") String startDate,
                                                                          @QueryParam("endDate") String endDate,
                                                                          @QueryParam("limit") Integer limit) {
        int resolvedLimit = limit == null ? 20 : limit;
        return managementService.getUsageAnalytics(
                customerId,
                modelId,
                parseInstant(startDate, "startDate"),
                parseInstant(endDate, "endDate"),
                resolvedLimit
        );
    }

    public record CustomerUpsertRequest(String id, String name, Boolean active, String changeType) {
    }

    public record CustomerUpdateRequest(String name, Boolean active, String changeType) {
    }

    public record ModelUpsertRequest(String id,
                                     String displayName,
                                     String provider,
                                     Boolean active,
                                     Integer defaultTimeCriterionSeconds,
                                     BigDecimal defaultTimePrice,
                                     Integer defaultTokenCriterion,
                                     BigDecimal defaultTokenPrice,
                                     String changeType) {
    }

    public record ModelUpdateRequest(String displayName,
                                     String provider,
                                     Boolean active,
                                     Integer defaultTimeCriterionSeconds,
                                     BigDecimal defaultTimePrice,
                                     Integer defaultTokenCriterion,
                                     BigDecimal defaultTokenPrice,
                                     String changeType) {
    }

    public record ApiKeyCreateRequest(String id, String customerId, String name, Boolean active, String changeType) {
    }

    public record ApiKeyUpdateRequest(String name, Boolean active, String changeType) {
    }

    public record ModelRouteUpsertRequest(String backendId, String baseUrl, Integer weight, Boolean active, String changeType) {
    }

    public record ModelRouteActiveRequest(boolean active, String changeType) {
    }

    public record ModelRouteWeightRequest(int weight, String changeType) {
    }

    public record CustomerModelPricingUpsertRequest(Integer timeCriterionSeconds,
                                                    BigDecimal timePrice,
                                                    Integer tokenCriterion,
                                                    BigDecimal tokenPrice,
                                                    String changeType) {
    }

    public record CustomerResponse(String id, String name, boolean active) {
        static CustomerResponse from(CustomerEntity entity) {
            return new CustomerResponse(entity.id, entity.name, entity.active);
        }
    }

    public record ModelResponse(String id,
                                String displayName,
                                String provider,
                                boolean active,
                                int defaultTimeCriterionSeconds,
                                BigDecimal defaultTimePrice,
                                int defaultTokenCriterion,
                                BigDecimal defaultTokenPrice) {
        static ModelResponse from(LlmModelEntity entity) {
            return new ModelResponse(
                    entity.id,
                    entity.displayName,
                    entity.provider,
                    entity.active,
                    entity.defaultTimeCriterionSeconds,
                    entity.defaultTimePrice,
                    entity.defaultTokenCriterion,
                    entity.defaultTokenPrice
            );
        }
    }

    public record ApiKeyResponse(String id, String customerId, String name, boolean active) {
        static ApiKeyResponse from(ApiKeyEntity entity) {
            return new ApiKeyResponse(entity.id, entity.customerId, entity.name, entity.active);
        }
    }

    public record ModelRouteResponse(String modelId,
                                     String backendId,
                                     String baseUrl,
                                     int weight,
                                     boolean active,
                                     int version,
                                     Instant updatedAt) {
        static ModelRouteResponse from(ModelRouteEntity entity) {
            return new ModelRouteResponse(entity.modelId, entity.backendId, entity.baseUrl, entity.weight, entity.active, entity.version, entity.updatedAt);
        }
    }

    public record CustomerModelPricingResponse(String customerId,
                                               String modelId,
                                               int timeCriterionSeconds,
                                               BigDecimal timePrice,
                                               int tokenCriterion,
                                               BigDecimal tokenPrice,
                                               Instant updatedAt) {
        static CustomerModelPricingResponse from(CustomerModelPricingEntity entity) {
            return new CustomerModelPricingResponse(
                    entity.customerId,
                    entity.modelId,
                    entity.timeCriterionSeconds,
                    entity.timePrice,
                    entity.tokenCriterion,
                    entity.tokenPrice,
                    entity.updatedAt
            );
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

    public record AuditEventResponse(Long eventId,
                                     String eventType,
                                     String actor,
                                     String entityType,
                                     String entityId,
                                     Map<String, Object> payload,
                                     Instant occurredAt,
                                     String prevHash,
                                     String eventHash,
                                     String signature) {
    }

    private AuditEventResponse toAuditEventResponse(AuditLogEntity entity) {
        Map<String, Object> payload = parsePayload(entity.payloadJson);
        return new AuditEventResponse(
                entity.eventId,
                entity.eventType,
                entity.actor,
                entity.entityType,
                entity.entityId,
                payload,
                entity.occurredAt,
                entity.prevHash,
                entity.eventHash,
                entity.signature
        );
    }

    private Map<String, Object> parsePayload(String payloadJson) {
        try {
            return objectMapper.readValue(payloadJson, new TypeReference<>() {
            });
        } catch (Exception exception) {
            throw new IllegalArgumentException("failed to parse audit payload", exception);
        }
    }

    private ManagementService.ChangeAuditContext auditContext(String actorId,
                                                              String sourceIp,
                                                              String requestId,
                                                              String reason,
                                                              String changeType,
                                                              String secondApproverId,
                                                              Integer bulkChangeSize) {
        return new ManagementService.ChangeAuditContext(
                Optional.ofNullable(actorId).filter(value -> !value.isBlank()).orElse("management-user"),
                Optional.ofNullable(sourceIp).filter(value -> !value.isBlank()).orElse("unknown"),
                Optional.ofNullable(requestId).filter(value -> !value.isBlank()).orElse("mgmt_" + UUID.randomUUID()),
                Optional.ofNullable(reason).filter(value -> !value.isBlank()).orElse("not_provided"),
                Optional.ofNullable(changeType).filter(value -> !value.isBlank()).orElse("general"),
                normalizeOptional(secondApproverId),
                bulkChangeSize == null ? 1 : Math.max(1, bulkChangeSize)
        );
    }

    private String normalizeOptional(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
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
