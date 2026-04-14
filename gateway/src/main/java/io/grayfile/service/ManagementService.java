package io.grayfile.service;

import io.grayfile.domain.ApiKeyEntity;
import io.grayfile.domain.AuditLogEntity;
import io.grayfile.domain.BillingWindowEntity;
import io.grayfile.domain.CustomerEntity;
import io.grayfile.domain.CustomerModelPricingEntity;
import io.grayfile.domain.LlmModelEntity;
import io.grayfile.domain.ModelRouteEntity;
import io.grayfile.persistence.ApiKeyRepository;
import io.grayfile.persistence.AuditLogRepository;
import io.grayfile.persistence.BillingWindowRepository;
import io.grayfile.persistence.CustomerRepository;
import io.grayfile.persistence.CustomerModelPricingRepository;
import io.grayfile.persistence.LlmModelRepository;
import io.grayfile.persistence.ModelRouteRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.NotFoundException;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.math.BigDecimal;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

@ApplicationScoped
public class ManagementService {

    private static final Pattern IDENTIFIER_PATTERN = Pattern.compile("^[A-Za-z0-9._:-]{1,80}$");
    private static final Pattern HOST_LITERAL_IPV4_PATTERN = Pattern.compile("^\\d+\\.\\d+\\.\\d+\\.\\d+$");

    private final CustomerRepository customerRepository;
    private final CustomerModelPricingRepository customerModelPricingRepository;
    private final LlmModelRepository llmModelRepository;
    private final ApiKeyRepository apiKeyRepository;
    private final BillingWindowRepository billingWindowRepository;
    private final AuditLogRepository auditLogRepository;
    private final AuditLogService auditLogService;
    private final AlertService alertService;
    private final BackendHealthcheckService backendHealthcheckService;
    private final ModelRouteRepository modelRouteRepository;
    private final PricingService pricingService;
    private final UsageAnalyticsService usageAnalyticsService;
    private final Event<ModelRoutesChangedEvent> modelRoutesChangedEvent;
    private final boolean twoPersonRuleEnabled;
    private final int bulkRoutingAlertThreshold;
    private final boolean requireActiveRouteGuardrail;

    public ManagementService(CustomerRepository customerRepository,
                             CustomerModelPricingRepository customerModelPricingRepository,
                             LlmModelRepository llmModelRepository,
                             ApiKeyRepository apiKeyRepository,
                             BillingWindowRepository billingWindowRepository,
                             AuditLogRepository auditLogRepository,
                             AuditLogService auditLogService,
                             AlertService alertService,
                             BackendHealthcheckService backendHealthcheckService,
                             ModelRouteRepository modelRouteRepository,
                             PricingService pricingService,
                             UsageAnalyticsService usageAnalyticsService,
                             Event<ModelRoutesChangedEvent> modelRoutesChangedEvent,
                             @ConfigProperty(name = "grayfile.management.two-person-rule.enabled", defaultValue = "false") boolean twoPersonRuleEnabled,
                             @ConfigProperty(name = "grayfile.management.alert.bulk-routing-threshold", defaultValue = "25") int bulkRoutingAlertThreshold,
                             @ConfigProperty(name = "grayfile.routing.guardrails.require-active-route", defaultValue = "true") boolean requireActiveRouteGuardrail) {
        this.customerRepository = customerRepository;
        this.customerModelPricingRepository = customerModelPricingRepository;
        this.llmModelRepository = llmModelRepository;
        this.apiKeyRepository = apiKeyRepository;
        this.billingWindowRepository = billingWindowRepository;
        this.auditLogRepository = auditLogRepository;
        this.auditLogService = auditLogService;
        this.alertService = alertService;
        this.backendHealthcheckService = backendHealthcheckService;
        this.modelRouteRepository = modelRouteRepository;
        this.pricingService = pricingService;
        this.usageAnalyticsService = usageAnalyticsService;
        this.modelRoutesChangedEvent = modelRoutesChangedEvent;
        this.twoPersonRuleEnabled = twoPersonRuleEnabled;
        this.bulkRoutingAlertThreshold = bulkRoutingAlertThreshold;
        this.requireActiveRouteGuardrail = requireActiveRouteGuardrail;
    }

    public List<CustomerEntity> listCustomers() {
        return customerRepository.list("order by id asc");
    }

    public CustomerEntity getCustomer(String customerId) {
        String normalizedCustomerId = normalizeIdentifier(customerId, "customer id");
        return customerRepository.findByIdOptional(normalizedCustomerId)
                .orElseThrow(() -> new NotFoundException("customer not found: " + normalizedCustomerId));
    }

    @Transactional
    public CustomerEntity createCustomer(String id, String name, Boolean active, ChangeAuditContext context) {
        id = normalizeIdentifier(id, "customer id");
        if (customerRepository.findByIdOptional(id).isPresent()) {
            throw new IllegalArgumentException("customer already exists: " + id);
        }
        CustomerEntity entity = new CustomerEntity();
        entity.id = id;
        entity.name = normalizeDisplayText(name, "customer name", 120);
        entity.active = active == null || active;
        customerRepository.persist(entity);

        Map<String, Object> newState = customerState(entity);
        auditManagementChange("CUSTOMER_CREATED", "customer", entity.id, context, Map.of(), newState);
        return entity;
    }

    @Transactional
    public CustomerEntity updateCustomer(String customerId, String name, Boolean active, ChangeAuditContext context) {
        CustomerEntity entity = getCustomer(customerId);
        Map<String, Object> oldState = customerState(entity);
        entity.name = normalizeDisplayText(name, "customer name", 120);
        entity.active = active == null || active;
        Map<String, Object> newState = customerState(entity);
        auditManagementChange("CUSTOMER_UPDATED", "customer", entity.id, context, oldState, newState);
        return entity;
    }

    @Transactional
    public CustomerEntity deactivateCustomer(String customerId, ChangeAuditContext context) {
        CustomerEntity entity = getCustomer(customerId);
        Map<String, Object> oldState = customerState(entity);
        entity.active = false;
        Map<String, Object> newState = customerState(entity);
        auditManagementChange("CUSTOMER_DEACTIVATED", "customer", entity.id, context, oldState, newState);
        return entity;
    }

    public List<LlmModelEntity> listModels() {
        return llmModelRepository.list("order by id asc");
    }

    public LlmModelEntity getModel(String modelId) {
        String normalizedModelId = normalizeIdentifier(modelId, "model id");
        return llmModelRepository.findByIdOptional(normalizedModelId)
                .orElseThrow(() -> new NotFoundException("model not found: " + normalizedModelId));
    }

    @Transactional
    public LlmModelEntity createModel(String id,
                                      String displayName,
                                      String provider,
                                      Boolean active,
                                      BigDecimal defaultTimePrice,
                                      BigDecimal defaultTokenPrice,
                                      ChangeAuditContext context) {
        id = normalizeIdentifier(id, "model id");
        enforceTwoPersonRuleIfRequired(context);
        if (llmModelRepository.findByIdOptional(id).isPresent()) {
            throw new IllegalArgumentException("model already exists: " + id);
        }
        LlmModelEntity entity = new LlmModelEntity();
        entity.id = id;
        entity.displayName = normalizeDisplayText(displayName, "model display name", 120);
        entity.provider = normalizeIdentifier(provider, "model provider");
        entity.active = active == null || active;
        entity.defaultTimePrice = pricingService.normalizeNullablePrice(defaultTimePrice);
        entity.defaultTokenPrice = pricingService.normalizeNullablePrice(defaultTokenPrice);
        llmModelRepository.persist(entity);

        Map<String, Object> newState = modelState(entity);
        auditManagementChange("MODEL_CREATED", "model", entity.id, context, Map.of(), newState);
        maybeAlertBulkRoutingChange(context, entity.id);
        return entity;
    }

    @Transactional
    public LlmModelEntity updateModel(String modelId,
                                      String displayName,
                                      String provider,
                                      Boolean active,
                                      BigDecimal defaultTimePrice,
                                      BigDecimal defaultTokenPrice,
                                      ChangeAuditContext context) {
        LlmModelEntity entity = getModel(modelId);
        enforceTwoPersonRuleIfRequired(context);

        Map<String, Object> oldState = modelState(entity);
        entity.displayName = normalizeDisplayText(displayName, "model display name", 120);
        entity.provider = normalizeIdentifier(provider, "model provider");
        entity.active = active == null || active;
        entity.defaultTimePrice = pricingService.normalizeNullablePrice(defaultTimePrice);
        entity.defaultTokenPrice = pricingService.normalizeNullablePrice(defaultTokenPrice);
        Map<String, Object> newState = modelState(entity);

        auditManagementChange("MODEL_UPDATED", "model", entity.id, context, oldState, newState);
        maybeAlertBulkRoutingChange(context, entity.id);
        if ((Boolean) oldState.get("active") && !(Boolean) newState.get("active")) {
            alertService.emitCritical(
                    "ACTIVE_MODEL_DISABLED",
                    "model",
                    entity.id,
                    context.actorId(),
                    context.requestId(),
                    context.sourceIp(),
                    context.reason(),
                    auditLogService.payloadOf("old_state", oldState, "new_state", newState)
            );
        }
        return entity;
    }

    @Transactional
    public LlmModelEntity deactivateModel(String modelId, ChangeAuditContext context) {
        LlmModelEntity entity = getModel(modelId);
        enforceTwoPersonRuleIfRequired(context);

        Map<String, Object> oldState = modelState(entity);
        entity.active = false;
        Map<String, Object> newState = modelState(entity);
        auditManagementChange("MODEL_DEACTIVATED", "model", entity.id, context, oldState, newState);

        if ((Boolean) oldState.get("active")) {
            alertService.emitCritical(
                    "ACTIVE_MODEL_DISABLED",
                    "model",
                    entity.id,
                    context.actorId(),
                    context.requestId(),
                    context.sourceIp(),
                    context.reason(),
                    auditLogService.payloadOf("old_state", oldState, "new_state", newState)
            );
        }
        maybeAlertBulkRoutingChange(context, entity.id);
        return entity;
    }


    public List<ModelRouteEntity> listModelRoutes(String modelId) {
        String normalizedModelId = normalizeIdentifier(modelId, "model id");
        getModel(normalizedModelId);
        return modelRouteRepository.listByModel(normalizedModelId);
    }

    @Transactional
    public ModelRouteEntity createModelRoute(String modelId,
                                             String backendId,
                                             String baseUrl,
                                             Integer weight,
                                             Boolean active,
                                             ChangeAuditContext context) {
        modelId = normalizeIdentifier(modelId, "model id");
        getModel(modelId);
        backendId = normalizeIdentifier(backendId, "backend id");
        if (modelRouteRepository.findByModelAndBackend(modelId, backendId).isPresent()) {
            throw new IllegalArgumentException("model route already exists for model=" + modelId + " backend=" + backendId);
        }
        ModelRouteEntity entity = new ModelRouteEntity();
        entity.modelId = modelId;
        entity.backendId = backendId;
        entity.baseUrl = normalizeBaseUrl(baseUrl);
        entity.weight = normalizeWeight(weight);
        entity.active = active == null || active;
        // A route can be defined ahead of time while the backend is still offline.
        // Availability is enforced only when the route is active.
        if (entity.active) {
            backendHealthcheckService.verifyReachable(entity.baseUrl);
        }
        entity.version = 1;
        entity.updatedAt = Instant.now();
        modelRouteRepository.persist(entity);

        auditManagementChange("MODEL_ROUTE_CREATED", "model_route", modelId + ":" + backendId, context, Map.of(), modelRouteState(entity));
        modelRoutesChangedEvent.fire(new ModelRoutesChangedEvent(modelId));
        return entity;
    }

    @Transactional
    public ModelRouteEntity setModelRouteActive(String modelId,
                                                String backendId,
                                                boolean active,
                                                ChangeAuditContext context) {
        ModelRouteEntity entity = modelRouteRepository.findByModelAndBackend(normalizeIdentifier(modelId, "model id"), normalizeIdentifier(backendId, "backend id"))
                .orElseThrow(() -> new NotFoundException("route not found for model=" + modelId + " backend=" + backendId));
        Map<String, Object> oldState = modelRouteState(entity);
        entity.active = active;
        // Activating a route is the operational handoff, so the backend must answer the healthcheck.
        if (entity.active) {
            backendHealthcheckService.verifyReachable(entity.baseUrl);
        }
        enforceActiveRouteGuardrail(entity.modelId);
        entity.version += 1;
        entity.updatedAt = Instant.now();
        Map<String, Object> newState = modelRouteState(entity);
        auditManagementChange("MODEL_ROUTE_UPDATED", "model_route", entity.modelId + ":" + entity.backendId, context, oldState, newState);
        modelRoutesChangedEvent.fire(new ModelRoutesChangedEvent(entity.modelId));
        return entity;
    }

    @Transactional
    public ModelRouteEntity setModelRouteWeight(String modelId,
                                                String backendId,
                                                int weight,
                                                ChangeAuditContext context) {
        ModelRouteEntity entity = modelRouteRepository.findByModelAndBackend(normalizeIdentifier(modelId, "model id"), normalizeIdentifier(backendId, "backend id"))
                .orElseThrow(() -> new NotFoundException("route not found for model=" + modelId + " backend=" + backendId));
        Map<String, Object> oldState = modelRouteState(entity);
        entity.weight = normalizeWeight(weight);
        if (entity.active) {
            backendHealthcheckService.verifyReachable(entity.baseUrl);
        }
        enforceActiveRouteGuardrail(entity.modelId);
        entity.version += 1;
        entity.updatedAt = Instant.now();
        Map<String, Object> newState = modelRouteState(entity);
        auditManagementChange("MODEL_ROUTE_UPDATED", "model_route", entity.modelId + ":" + entity.backendId, context, oldState, newState);
        modelRoutesChangedEvent.fire(new ModelRoutesChangedEvent(entity.modelId));
        return entity;
    }

    @Transactional
    public void deleteModelRoute(String modelId, String backendId, ChangeAuditContext context) {
        String normalizedModelId = normalizeIdentifier(modelId, "model id");
        String normalizedBackendId = normalizeIdentifier(backendId, "backend id");
        ModelRouteEntity existing = modelRouteRepository.findByModelAndBackend(normalizedModelId, normalizedBackendId)
                .orElseThrow(() -> new NotFoundException("route not found for model=" + normalizedModelId + " backend=" + normalizedBackendId));
        Map<String, Object> oldState = modelRouteState(existing);
        modelRouteRepository.delete(existing);
        enforceActiveRouteGuardrail(normalizedModelId);
        auditManagementChange("MODEL_ROUTE_DELETED", "model_route", normalizedModelId + ":" + normalizedBackendId, context, oldState, Map.of());
        modelRoutesChangedEvent.fire(new ModelRoutesChangedEvent(normalizedModelId));
    }

    public List<ApiKeyEntity> listApiKeys() {
        return apiKeyRepository.listAllOrdered();
    }

    public ApiKeyEntity getApiKey(String apiKeyId) {
        String normalizedApiKeyId = normalizeIdentifier(apiKeyId, "api key id");
        return apiKeyRepository.findByIdOptional(normalizedApiKeyId)
                .orElseThrow(() -> new NotFoundException("api key not found: " + normalizedApiKeyId));
    }

    @Transactional
    public ApiKeyEntity createApiKey(String id, String customerId, String name, Boolean active, ChangeAuditContext context) {
        id = normalizeIdentifier(id, "api key id");
        if (apiKeyRepository.findByIdOptional(id).isPresent()) {
            throw new IllegalArgumentException("api key already exists: " + id);
        }
        CustomerEntity customer = getCustomer(normalizeIdentifier(customerId, "api key customer id"));
        ApiKeyEntity entity = new ApiKeyEntity();
        entity.id = id;
        entity.customerId = customer.id;
        entity.name = normalizeDisplayText(name, "api key name", 120);
        entity.active = active == null || active;
        apiKeyRepository.persist(entity);

        Map<String, Object> newState = apiKeyState(entity);
        auditManagementChange("API_KEY_CREATED", "api_key", entity.id, context, Map.of(), newState);
        return entity;
    }

    @Transactional
    public ApiKeyEntity updateApiKey(String apiKeyId, String name, Boolean active, ChangeAuditContext context) {
        ApiKeyEntity entity = getApiKey(apiKeyId);
        Map<String, Object> oldState = apiKeyState(entity);
        entity.name = normalizeDisplayText(name, "api key name", 120);
        entity.active = active == null || active;
        Map<String, Object> newState = apiKeyState(entity);
        auditManagementChange("API_KEY_UPDATED", "api_key", entity.id, context, oldState, newState);
        return entity;
    }

    @Transactional
    public ApiKeyEntity deactivateApiKey(String apiKeyId, ChangeAuditContext context) {
        ApiKeyEntity entity = getApiKey(apiKeyId);
        Map<String, Object> oldState = apiKeyState(entity);
        entity.active = false;
        Map<String, Object> newState = apiKeyState(entity);
        auditManagementChange("API_KEY_DEACTIVATED", "api_key", entity.id, context, oldState, newState);

        alertService.emitCritical(
                "API_KEY_REMOVED",
                "api_key",
                entity.id,
                context.actorId(),
                context.requestId(),
                context.sourceIp(),
                context.reason(),
                auditLogService.payloadOf("old_state", oldState, "new_state", newState)
        );
        return entity;
    }

    public List<BillingWindowEntity> listBillingWindows(String customerId, String apiKeyId, Instant startFrom, Instant endTo) {
        customerId = normalizeOptional(customerId);
        apiKeyId = normalizeOptional(apiKeyId);
        if (startFrom != null && endTo != null && startFrom.isAfter(endTo)) {
            throw new IllegalArgumentException("startDate must be before or equal to endDate");
        }
        return billingWindowRepository.listFiltered(customerId, apiKeyId, startFrom, endTo);
    }

    public List<CustomerModelPricingEntity> listCustomerPricingForModel(String modelId) {
        String normalizedModelId = normalizeIdentifier(modelId, "model id");
        getModel(normalizedModelId);
        return customerModelPricingRepository.listByModel(normalizedModelId);
    }

    @Transactional
    public CustomerModelPricingEntity upsertCustomerPricing(String modelId,
                                                            String customerId,
                                                            BigDecimal timePrice,
                                                            BigDecimal tokenPrice,
                                                            ChangeAuditContext context) {
        enforceTwoPersonRuleIfRequired(context);
        String normalizedModelId = normalizeIdentifier(modelId, "model id");
        String normalizedCustomerId = normalizeIdentifier(customerId, "customer id");
        getModel(normalizedModelId);
        getCustomer(normalizedCustomerId);

        CustomerModelPricingEntity entity = customerModelPricingRepository.findByCustomerAndModel(normalizedCustomerId, normalizedModelId).orElse(null);
        Map<String, Object> oldState = entity == null ? Map.of() : customerPricingState(entity);
        if (entity == null) {
            entity = new CustomerModelPricingEntity();
            entity.id = UUID.randomUUID();
            entity.customerId = normalizedCustomerId;
            entity.modelId = normalizedModelId;
            customerModelPricingRepository.persist(entity);
        }
        entity.timePrice = pricingService.normalizePrice(timePrice, "customer-model time price");
        entity.tokenPrice = pricingService.normalizePrice(tokenPrice, "customer-model token price");
        entity.updatedAt = Instant.now();

        auditManagementChange("CUSTOMER_MODEL_PRICING_UPSERTED", "customer_model_pricing", normalizedCustomerId + ":" + normalizedModelId, context, oldState, customerPricingState(entity));
        return entity;
    }

    @Transactional
    public void deleteCustomerPricing(String modelId, String customerId, ChangeAuditContext context) {
        enforceTwoPersonRuleIfRequired(context);
        String normalizedModelId = normalizeIdentifier(modelId, "model id");
        String normalizedCustomerId = normalizeIdentifier(customerId, "customer id");
        CustomerModelPricingEntity entity = customerModelPricingRepository.findByCustomerAndModel(normalizedCustomerId, normalizedModelId)
                .orElseThrow(() -> new NotFoundException("customer pricing not found for customer=" + normalizedCustomerId + " model=" + normalizedModelId));
        Map<String, Object> oldState = customerPricingState(entity);
        customerModelPricingRepository.delete(entity);
        auditManagementChange("CUSTOMER_MODEL_PRICING_DELETED", "customer_model_pricing", normalizedCustomerId + ":" + normalizedModelId, context, oldState, Map.of());
    }

    public List<AuditLogEntity> listAuditEvents(String eventType,
                                                Instant startFrom,
                                                Instant endTo,
                                                String entityType,
                                                String entityId,
                                                int limit) {
        if (startFrom != null && endTo != null && startFrom.isAfter(endTo)) {
            throw new IllegalArgumentException("startDate must be before or equal to endDate");
        }
        int normalizedLimit = Math.max(1, Math.min(limit, 500));
        return auditLogRepository.listFiltered(
                normalizeOptional(eventType),
                startFrom,
                endTo,
                normalizeOptional(entityType),
                normalizeOptional(entityId),
                normalizedLimit
        );
    }

    public UsageAnalyticsService.UsageAnalyticsResponse getUsageAnalytics(String customerId,
                                                                          String modelId,
                                                                          Instant startFrom,
                                                                          Instant endTo,
                                                                          int limit) {
        if (startFrom != null && endTo != null && startFrom.isAfter(endTo)) {
            throw new IllegalArgumentException("startDate must be before or equal to endDate");
        }
        return usageAnalyticsService.buildAnalytics(
                normalizeOptional(customerId),
                normalizeOptional(modelId),
                startFrom,
                endTo,
                limit
        );
    }

    public UsageScopeValidation validateUsageScope(String customerId, String apiKeyId, String modelId) {
        String normalizedCustomerId = normalizeIdentifier(customerId, "customer id");
        String normalizedApiKeyId = normalizeIdentifier(apiKeyId, "api key id");
        String normalizedModelId = normalizeIdentifier(modelId, "model id");

        CustomerEntity customer = customerRepository.findByIdOptional(normalizedCustomerId).orElse(null);
        if (customer == null || !customer.active) {
            return UsageScopeValidation.failure("unknown or inactive customer: " + normalizedCustomerId);
        }

        ApiKeyEntity apiKey = apiKeyRepository.findByIdOptional(normalizedApiKeyId).orElse(null);
        if (apiKey == null || !apiKey.active) {
            return UsageScopeValidation.failure("unknown or inactive api key: " + normalizedApiKeyId);
        }

        if (!apiKey.customerId.equals(normalizedCustomerId)) {
            return UsageScopeValidation.failure("api key " + normalizedApiKeyId + " does not belong to customer " + normalizedCustomerId);
        }

        LlmModelEntity model = llmModelRepository.findByIdOptional(normalizedModelId).orElse(null);
        if (model == null || !model.active) {
            return UsageScopeValidation.failure("unknown or inactive model: " + normalizedModelId);
        }

        return UsageScopeValidation.success();
    }

    private void maybeAlertBulkRoutingChange(ChangeAuditContext context, String entityId) {
        if (context.bulkChangeSize() >= bulkRoutingAlertThreshold && isSensitiveChange(context.changeType())) {
            alertService.emitCritical(
                    "MASS_ROUTING_CHANGE",
                    "model",
                    entityId,
                    context.actorId(),
                    context.requestId(),
                    context.sourceIp(),
                    context.reason(),
                    auditLogService.payloadOf("bulk_change_size", context.bulkChangeSize(), "threshold", bulkRoutingAlertThreshold)
            );
        }
    }

    private void enforceTwoPersonRuleIfRequired(ChangeAuditContext context) {
        if (!twoPersonRuleEnabled || !isSensitiveChange(context.changeType())) {
            return;
        }
        if (context.secondApproverId() == null || context.secondApproverId().isBlank()) {
            throw new IllegalArgumentException("x-second-approver-id is required for sensitive " + context.changeType() + " changes");
        }
        if (context.actorId().equalsIgnoreCase(context.secondApproverId())) {
            throw new IllegalArgumentException("x-second-approver-id must differ from actor_id");
        }
    }

    private boolean isSensitiveChange(String changeType) {
        return Set.of("routing", "pricing").contains(changeType);
    }

    private void auditManagementChange(String eventType,
                                       String entityType,
                                       String entityId,
                                       ChangeAuditContext context,
                                       Map<String, Object> oldState,
                                       Map<String, Object> newState) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("actor_id", context.actorId());
        payload.put("source_ip", context.sourceIp());
        payload.put("request_id", context.requestId());
        payload.put("reason", context.reason());
        payload.put("change_type", context.changeType());
        payload.put("second_approver_id", context.secondApproverId());
        payload.put("old_state", oldState);
        payload.put("new_state", newState);
        payload.put("bulk_change_size", context.bulkChangeSize());
        auditLogService.logEvent(eventType, context.actorId(), entityType, entityId, payload, Instant.now());
    }

    private Map<String, Object> customerState(CustomerEntity entity) {
        return auditLogService.payloadOf("id", entity.id, "name", entity.name, "active", entity.active);
    }

    private Map<String, Object> modelState(LlmModelEntity entity) {
        return auditLogService.payloadOf(
                "id", entity.id,
                "display_name", entity.displayName,
                "provider", entity.provider,
                "active", entity.active,
                "default_time_price", entity.defaultTimePrice,
                "default_token_price", entity.defaultTokenPrice
        );
    }

    private Map<String, Object> modelRouteState(ModelRouteEntity entity) {
        return auditLogService.payloadOf(
                "model_id", entity.modelId,
                "backend_id", entity.backendId,
                "base_url", entity.baseUrl,
                "weight", entity.weight,
                "active", entity.active,
                "version", entity.version,
                "updated_at", entity.updatedAt
        );
    }

    private Map<String, Object> apiKeyState(ApiKeyEntity entity) {
        return auditLogService.payloadOf(
                "id", entity.id,
                "customer_id", entity.customerId,
                "name", entity.name,
                "active", entity.active
        );
    }

    private Map<String, Object> customerPricingState(CustomerModelPricingEntity entity) {
        return auditLogService.payloadOf(
                "customer_id", entity.customerId,
                "model_id", entity.modelId,
                "time_price", entity.timePrice,
                "token_price", entity.tokenPrice,
                "updated_at", entity.updatedAt
        );
    }

    private int normalizeWeight(Integer weight) {
        if (weight == null) {
            return 100;
        }
        return normalizeWeight(weight.intValue());
    }

    private int normalizeWeight(int weight) {
        if (weight <= 0) {
            throw new IllegalArgumentException("route weight must be > 0");
        }
        return weight;
    }

    private String normalizeBaseUrl(String baseUrl) {
        String normalized = normalizeRequired(baseUrl, "base url");
        try {
            URI uri = new URI(normalized);
            if (!uri.isAbsolute()) {
                throw new IllegalArgumentException("base url must be absolute (http/https): " + normalized);
            }
            String scheme = uri.getScheme();
            if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
                throw new IllegalArgumentException("base url must use http or https: " + normalized);
            }
            if (uri.getHost() == null || uri.getHost().isBlank()) {
                throw new IllegalArgumentException("base url must include a host: " + normalized);
            }
            if (uri.getUserInfo() != null && !uri.getUserInfo().isBlank()) {
                throw new IllegalArgumentException("base url must not include credentials: " + normalized);
            }
            if (isUnsafeHost(uri.getHost())) {
                throw new IllegalArgumentException("base url host is not allowed for routing: " + normalized);
            }
            if (uri.getRawQuery() != null || uri.getRawFragment() != null) {
                throw new IllegalArgumentException("base url must not include query params or fragments: " + normalized);
            }
            return uri.toString().endsWith("/") ? uri.toString().substring(0, uri.toString().length() - 1) : uri.toString();
        } catch (URISyntaxException exception) {
            throw new IllegalArgumentException("invalid base url syntax: " + normalized, exception);
        }
    }

    private void enforceActiveRouteGuardrail(String modelId) {
        if (!requireActiveRouteGuardrail) {
            return;
        }
        long activeRoutes = modelRouteRepository.listActiveByModel(modelId).size();
        if (activeRoutes == 0) {
            throw new IllegalArgumentException("at least one active route must exist for model: " + modelId + " (config rolled back)");
        }
    }

    private String normalizeRequired(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(label + " is required");
        }
        return value.trim();
    }

    private String normalizeIdentifier(String value, String label) {
        String normalized = normalizeRequired(value, label);
        if (!IDENTIFIER_PATTERN.matcher(normalized).matches()) {
            throw new IllegalArgumentException(label + " must match " + IDENTIFIER_PATTERN.pattern());
        }
        return normalized;
    }

    private String normalizeOptional(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private String normalizeDisplayText(String value, String label, int maxLength) {
        String normalized = normalizeRequired(value, label);
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException(label + " must be <= " + maxLength + " characters");
        }
        for (int index = 0; index < normalized.length(); index += 1) {
            if (Character.isISOControl(normalized.charAt(index))) {
                throw new IllegalArgumentException(label + " must not contain control characters");
            }
        }
        return normalized;
    }

    private boolean isUnsafeHost(String host) {
        String normalizedHost = host.trim().toLowerCase();
        if (normalizedHost.equals("localhost") || normalizedHost.equals("0.0.0.0") || normalizedHost.equals("::1") || normalizedHost.endsWith(".localhost")) {
            return true;
        }
        if (HOST_LITERAL_IPV4_PATTERN.matcher(normalizedHost).matches()) {
            String[] octets = normalizedHost.split("\\.");
            int first = Integer.parseInt(octets[0]);
            int second = Integer.parseInt(octets[1]);
            return first == 10
                    || first == 127
                    || first == 0
                    || (first == 169 && second == 254)
                    || (first == 172 && second >= 16 && second <= 31)
                    || (first == 192 && second == 168);
        }
        return normalizedHost.startsWith("fc") || normalizedHost.startsWith("fd");
    }

    public record UsageScopeValidation(boolean valid, String message) {
        public static UsageScopeValidation success() {
            return new UsageScopeValidation(true, null);
        }

        public static UsageScopeValidation failure(String message) {
            return new UsageScopeValidation(false, message);
        }
    }

    public record ChangeAuditContext(String actorId,
                                     String sourceIp,
                                     String requestId,
                                     String reason,
                                     String changeType,
                                     String secondApproverId,
                                     int bulkChangeSize) {
    }
}
