package io.grayfile.service;

import io.grayfile.domain.ApiKeyEntity;
import io.grayfile.domain.BillingWindowEntity;
import io.grayfile.domain.CustomerEntity;
import io.grayfile.domain.LlmModelEntity;
import io.grayfile.persistence.ApiKeyRepository;
import io.grayfile.persistence.BillingWindowRepository;
import io.grayfile.persistence.CustomerRepository;
import io.grayfile.persistence.LlmModelRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.NotFoundException;

import java.time.Instant;
import java.util.List;

@ApplicationScoped
public class ManagementService {

    private final CustomerRepository customerRepository;
    private final LlmModelRepository llmModelRepository;
    private final ApiKeyRepository apiKeyRepository;
    private final BillingWindowRepository billingWindowRepository;

    public ManagementService(CustomerRepository customerRepository,
                             LlmModelRepository llmModelRepository,
                             ApiKeyRepository apiKeyRepository,
                             BillingWindowRepository billingWindowRepository) {
        this.customerRepository = customerRepository;
        this.llmModelRepository = llmModelRepository;
        this.apiKeyRepository = apiKeyRepository;
        this.billingWindowRepository = billingWindowRepository;
    }

    public List<CustomerEntity> listCustomers() {
        return customerRepository.list("order by id asc");
    }

    public CustomerEntity getCustomer(String customerId) {
        return customerRepository.findByIdOptional(customerId)
                .orElseThrow(() -> new NotFoundException("customer not found: " + customerId));
    }

    @Transactional
    public CustomerEntity createCustomer(String id, String name, Boolean active) {
        id = normalizeRequired(id, "customer id");
        if (customerRepository.findByIdOptional(id).isPresent()) {
            throw new IllegalArgumentException("customer already exists: " + id);
        }
        CustomerEntity entity = new CustomerEntity();
        entity.id = id;
        entity.name = normalizeRequired(name, "customer name");
        entity.active = active == null || active;
        customerRepository.persist(entity);
        return entity;
    }

    @Transactional
    public CustomerEntity updateCustomer(String customerId, String name, Boolean active) {
        CustomerEntity entity = getCustomer(customerId);
        entity.name = normalizeRequired(name, "customer name");
        entity.active = active == null || active;
        return entity;
    }

    @Transactional
    public CustomerEntity deactivateCustomer(String customerId) {
        CustomerEntity entity = getCustomer(customerId);
        entity.active = false;
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
    public LlmModelEntity createModel(String id, String displayName, String provider, Boolean active) {
        id = normalizeRequired(id, "model id");
        if (llmModelRepository.findByIdOptional(id).isPresent()) {
            throw new IllegalArgumentException("model already exists: " + id);
        }
        LlmModelEntity entity = new LlmModelEntity();
        entity.id = id;
        entity.displayName = normalizeRequired(displayName, "model display name");
        entity.provider = normalizeRequired(provider, "model provider");
        entity.active = active == null || active;
        llmModelRepository.persist(entity);
        return entity;
    }

    @Transactional
    public LlmModelEntity updateModel(String modelId, String displayName, String provider, Boolean active) {
        LlmModelEntity entity = getModel(modelId);
        entity.displayName = normalizeRequired(displayName, "model display name");
        entity.provider = normalizeRequired(provider, "model provider");
        entity.active = active == null || active;
        return entity;
    }

    @Transactional
    public LlmModelEntity deactivateModel(String modelId) {
        LlmModelEntity entity = getModel(modelId);
        entity.active = false;
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
    public ApiKeyEntity createApiKey(String id, String customerId, String name, Boolean active) {
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
        return entity;
    }

    @Transactional
    public ApiKeyEntity updateApiKey(String apiKeyId, String name, Boolean active) {
        ApiKeyEntity entity = getApiKey(apiKeyId);
        entity.name = normalizeRequired(name, "api key name");
        entity.active = active == null || active;
        return entity;
    }

    @Transactional
    public ApiKeyEntity deactivateApiKey(String apiKeyId) {
        ApiKeyEntity entity = getApiKey(apiKeyId);
        entity.active = false;
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
}
