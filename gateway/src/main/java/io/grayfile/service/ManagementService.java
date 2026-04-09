package io.grayfile.service;

import io.grayfile.domain.ApiKeyEntity;
import io.grayfile.domain.AuditLogEntity;
import io.grayfile.domain.BillingWindowEntity;
import io.grayfile.domain.CustomerEntity;
import io.grayfile.domain.LlmModelEntity;
import io.grayfile.persistence.ApiKeyRepository;
import io.grayfile.persistence.AuditLogRepository;
import io.grayfile.persistence.BillingWindowRepository;
import io.grayfile.persistence.CustomerRepository;
import io.grayfile.persistence.LlmModelRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.NotFoundException;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@ApplicationScoped
public class ManagementService {

    private final CustomerRepository customerRepository;
    private final LlmModelRepository llmModelRepository;
    private final ApiKeyRepository apiKeyRepository;
    private final BillingWindowRepository billingWindowRepository;
    private final AuditLogRepository auditLogRepository;
    private final AuditLogService auditLogService;
    private final AlertService alertService;
    private final boolean twoPersonRuleEnabled;
    private final int bulkRoutingAlertThreshold;

    public ManagementService(CustomerRepository customerRepository,
                             LlmModelRepository llmModelRepository,
                             ApiKeyRepository apiKeyRepository,
                             BillingWindowRepository billingWindowRepository,
                             AuditLogRepository auditLogRepository,
                             AuditLogService auditLogService,
                             AlertService alertService,
                             @ConfigProperty(name = "grayfile.management.two-person-rule.enabled", defaultValue = "false") boolean twoPersonRuleEnabled,
                             @ConfigProperty(name = "grayfile.management.alert.bulk-routing-threshold", defaultValue = "25") int bulkRoutingAlertThreshold) {
        this.customerRepository = customerRepository;
        this.llmModelRepository = llmModelRepository;
        this.apiKeyRepository = apiKeyRepository;
        this.billingWindowRepository = billingWindowRepository;
        this.auditLogRepository = auditLogRepository;
        this.auditLogService = auditLogService;
        this.alertService = alertService;
        this.twoPersonRuleEnabled = twoPersonRuleEnabled;
        this.bulkRoutingAlertThreshold = bulkRoutingAlertThreshold;
    }

    public List<CustomerEntity> listCustomers() {
        return customerRepository.list("order by id asc");
    }

    public CustomerEntity getCustomer(String customerId) {
        return customerRepository.findByIdOptional(customerId)
                .orElseThrow(() -> new NotFoundException("customer not found: " + customerId));
    }

    @Transactional
    public CustomerEntity createCustomer(String id, String name, Boolean active, ChangeAuditContext context) {
        id = normalizeRequired(id, "customer id");
        if (customerRepository.findByIdOptional(id).isPresent()) {
            throw new IllegalArgumentException("customer already exists: " + id);
        }
        CustomerEntity entity = new CustomerEntity();
        entity.id = id;
        entity.name = normalizeRequired(name, "customer name");
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
        entity.name = normalizeRequired(name, "customer name");
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
        return llmModelRepository.findByIdOptional(modelId)
                .orElseThrow(() -> new NotFoundException("model not found: " + modelId));
    }

    @Transactional
    public LlmModelEntity createModel(String id, String displayName, String provider, Boolean active, ChangeAuditContext context) {
        id = normalizeRequired(id, "model id");
        enforceTwoPersonRuleIfRequired(context);
        if (llmModelRepository.findByIdOptional(id).isPresent()) {
            throw new IllegalArgumentException("model already exists: " + id);
        }
        LlmModelEntity entity = new LlmModelEntity();
        entity.id = id;
        entity.displayName = normalizeRequired(displayName, "model display name");
        entity.provider = normalizeRequired(provider, "model provider");
        entity.active = active == null || active;
        llmModelRepository.persist(entity);

        Map<String, Object> newState = modelState(entity);
        auditManagementChange("MODEL_CREATED", "model", entity.id, context, Map.of(), newState);
        maybeAlertBulkRoutingChange(context, entity.id);
        return entity;
    }

    @Transactional
    public LlmModelEntity updateModel(String modelId, String displayName, String provider, Boolean active, ChangeAuditContext context) {
        LlmModelEntity entity = getModel(modelId);
        enforceTwoPersonRuleIfRequired(context);

        Map<String, Object> oldState = modelState(entity);
        entity.displayName = normalizeRequired(displayName, "model display name");
        entity.provider = normalizeRequired(provider, "model provider");
        entity.active = active == null || active;
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

    public List<ApiKeyEntity> listApiKeys() {
        return apiKeyRepository.listAllOrdered();
    }

    public ApiKeyEntity getApiKey(String apiKeyId) {
        return apiKeyRepository.findByIdOptional(apiKeyId)
                .orElseThrow(() -> new NotFoundException("api key not found: " + apiKeyId));
    }

    @Transactional
    public ApiKeyEntity createApiKey(String id, String customerId, String name, Boolean active, ChangeAuditContext context) {
        id = normalizeRequired(id, "api key id");
        if (apiKeyRepository.findByIdOptional(id).isPresent()) {
            throw new IllegalArgumentException("api key already exists: " + id);
        }
        CustomerEntity customer = getCustomer(normalizeRequired(customerId, "api key customer id"));
        ApiKeyEntity entity = new ApiKeyEntity();
        entity.id = id;
        entity.customerId = customer.id;
        entity.name = normalizeRequired(name, "api key name");
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
        entity.name = normalizeRequired(name, "api key name");
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

    public UsageScopeValidation validateUsageScope(String customerId, String apiKeyId, String modelId) {
        CustomerEntity customer = customerRepository.findByIdOptional(customerId).orElse(null);
        if (customer == null || !customer.active) {
            return UsageScopeValidation.failure("unknown or inactive customer: " + customerId);
        }

        ApiKeyEntity apiKey = apiKeyRepository.findByIdOptional(apiKeyId).orElse(null);
        if (apiKey == null || !apiKey.active) {
            return UsageScopeValidation.failure("unknown or inactive api key: " + apiKeyId);
        }

        if (!apiKey.customerId.equals(customerId)) {
            return UsageScopeValidation.failure("api key " + apiKeyId + " does not belong to customer " + customerId);
        }

        LlmModelEntity model = llmModelRepository.findByIdOptional(modelId).orElse(null);
        if (model == null || !model.active) {
            return UsageScopeValidation.failure("unknown or inactive model: " + modelId);
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
                "active", entity.active
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

    private String normalizeRequired(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(label + " is required");
        }
        return value.trim();
    }

    private String normalizeOptional(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
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
