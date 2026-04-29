package io.grayfile.litellm;

import io.grayfile.domain.ApiKeyEntity;
import io.grayfile.domain.LiteLlmResourceEntity;
import io.grayfile.domain.LlmModelEntity;
import io.grayfile.domain.ModelRouteEntity;
import io.grayfile.persistence.ApiKeyRepository;
import io.grayfile.persistence.LiteLlmResourceRepository;
import io.grayfile.persistence.LlmModelRepository;
import io.grayfile.persistence.ModelRouteRepository;
import io.grayfile.service.AuditLogService;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@ApplicationScoped
public class LiteLlmSyncService {

    private static final String MODEL_DEPLOYMENT = "model_deployment";
    private static final String VIRTUAL_KEY = "virtual_key";

    private final LiteLlmAdminClient client;
    private final LiteLlmResourceRepository resourceRepository;
    private final LlmModelRepository modelRepository;
    private final ModelRouteRepository routeRepository;
    private final ApiKeyRepository apiKeyRepository;
    private final AuditLogService auditLogService;

    public LiteLlmSyncService(LiteLlmAdminClient client,
                              LiteLlmResourceRepository resourceRepository,
                              LlmModelRepository modelRepository,
                              ModelRouteRepository routeRepository,
                              ApiKeyRepository apiKeyRepository,
                              AuditLogService auditLogService) {
        this.client = client;
        this.resourceRepository = resourceRepository;
        this.modelRepository = modelRepository;
        this.routeRepository = routeRepository;
        this.apiKeyRepository = apiKeyRepository;
        this.auditLogService = auditLogService;
    }

    @Transactional
    public LiteLlmResourceEntity syncModelRoute(ModelRouteEntity route, String actor, String requestId) {
        markRoutePending(route);
        Map<String, Object> litellmParams = new LinkedHashMap<>();
        litellmParams.put("model", route.liteLlmModel == null || route.liteLlmModel.isBlank() ? route.modelId : route.liteLlmModel);
        if (route.apiBase != null && !route.apiBase.isBlank()) {
            litellmParams.put("api_base", route.apiBase);
        }
        if (route.secretRef != null && !route.secretRef.isBlank()) {
            litellmParams.put("api_key", "os.environ/" + route.secretRef);
        }
        if (route.apiVersion != null && !route.apiVersion.isBlank()) {
            litellmParams.put("api_version", route.apiVersion);
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model_name", route.modelId);
        payload.put("litellm_params", litellmParams);
        payload.put("model_info", Map.of(
                "id", route.deploymentId == null || route.deploymentId.isBlank() ? route.backendId : route.deploymentId,
                "provider", route.provider == null ? "" : route.provider
        ));

        LiteLlmAdminClient.LiteLlmCallResult result = client.upsertModel(payload);
        LiteLlmResourceEntity mapping = updateMapping(
                "model_route",
                route.modelId + ":" + route.backendId,
                MODEL_DEPLOYMENT,
                result.success() ? route.deploymentId : null,
                result
        );
        applyRouteSyncResult(route, result);
        auditSync("LITELLM_MODEL_ROUTE_SYNCED", actor, requestId, route.modelId + ":" + route.backendId, result);
        return mapping;
    }

    @Transactional
    public LiteLlmResourceEntity syncApiKey(ApiKeyEntity apiKey, String actor, String requestId) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("key_alias", apiKey.id);
        payload.put("metadata", Map.of(
                "grayfile_api_key_id", apiKey.id,
                "grayfile_customer_id", apiKey.customerId
        ));
        payload.put("models", modelRepository.list("active = true order by id asc").stream().map(model -> model.id).toList());

        LiteLlmAdminClient.LiteLlmCallResult result = client.generateKey(payload);
        LiteLlmResourceEntity mapping = updateMapping("api_key", apiKey.id, VIRTUAL_KEY, result.resourceId(), result);
        auditSync("LITELLM_API_KEY_SYNCED", actor, requestId, apiKey.id, result);
        return mapping;
    }

    @Transactional
    public LiteLlmResourceEntity disableApiKey(ApiKeyEntity apiKey, String actor, String requestId) {
        LiteLlmResourceEntity existing = resourceRepository.findMapping("api_key", apiKey.id, VIRTUAL_KEY).orElse(null);
        String key = existing == null || existing.liteLlmResourceId == null ? apiKey.id : existing.liteLlmResourceId;
        LiteLlmAdminClient.LiteLlmCallResult result = client.blockKey(key);
        LiteLlmResourceEntity mapping = updateMapping("api_key", apiKey.id, VIRTUAL_KEY, key, result);
        if (result.success()) {
            mapping.lastSyncStatus = LiteLlmProvisioningStatus.DISABLED.value();
        }
        auditSync("LITELLM_API_KEY_DISABLED", actor, requestId, apiKey.id, result);
        return mapping;
    }

    @Transactional
    public List<LiteLlmResourceEntity> listResources() {
        return resourceRepository.listAllOrdered();
    }

    @Transactional
    public ReconciliationResult reconcile(String actor, String requestId) {
        int attempted = 0;
        int failed = 0;
        for (ModelRouteEntity route : routeRepository.listAll()) {
            attempted += 1;
            if (!syncModelRoute(route, actor, requestId).lastSyncStatus.equals(LiteLlmProvisioningStatus.SYNCED.value())) {
                failed += 1;
            }
        }
        for (ApiKeyEntity apiKey : apiKeyRepository.listAllOrdered()) {
            attempted += 1;
            LiteLlmResourceEntity mapping = apiKey.active
                    ? syncApiKey(apiKey, actor, requestId)
                    : disableApiKey(apiKey, actor, requestId);
            if (mapping.lastSyncStatus.equals(LiteLlmProvisioningStatus.FAILED.value())) {
                failed += 1;
            }
        }
        return new ReconciliationResult(attempted, failed, Instant.now(), client.syncEnabled(), client.dryRun());
    }

    @Scheduled(every = "{grayfile.litellm.reconcile.every}")
    void scheduledReconcile() {
        if (client.syncEnabled()) {
            reconcile("litellm-reconciler", "litellm_reconcile_" + UUID.randomUUID());
        }
    }

    public LiteLlmAdminClient.LiteLlmCallResult health() {
        return client.health();
    }

    private void markRoutePending(ModelRouteEntity route) {
        route.lastSyncStatus = LiteLlmProvisioningStatus.PENDING.value();
        route.lastSyncError = null;
    }

    private void applyRouteSyncResult(ModelRouteEntity route, LiteLlmAdminClient.LiteLlmCallResult result) {
        route.lastSyncStatus = result.success() ? LiteLlmProvisioningStatus.SYNCED.value() : LiteLlmProvisioningStatus.FAILED.value();
        route.lastSyncError = result.error();
        route.lastSyncedAt = result.success() ? Instant.now() : route.lastSyncedAt;
    }

    private LiteLlmResourceEntity updateMapping(String entityType,
                                                String entityId,
                                                String resourceType,
                                                String resourceId,
                                                LiteLlmAdminClient.LiteLlmCallResult result) {
        LiteLlmResourceEntity mapping = resourceRepository.findMapping(entityType, entityId, resourceType).orElseGet(() -> {
            LiteLlmResourceEntity entity = new LiteLlmResourceEntity();
            entity.id = UUID.randomUUID();
            entity.entityType = entityType;
            entity.entityId = entityId;
            entity.liteLlmResourceType = resourceType;
            return entity;
        });
        if (resourceId != null && !resourceId.isBlank()) {
            mapping.liteLlmResourceId = resourceId;
        }
        mapping.lastSyncStatus = result.success() ? LiteLlmProvisioningStatus.SYNCED.value() : LiteLlmProvisioningStatus.FAILED.value();
        mapping.lastSyncError = result.error();
        mapping.lastSyncedAt = result.success() ? Instant.now() : mapping.lastSyncedAt;
        mapping.updatedAt = Instant.now();
        if (!resourceRepository.getEntityManager().contains(mapping)) {
            resourceRepository.persist(mapping);
        }
        return mapping;
    }

    private void auditSync(String eventType,
                           String actor,
                           String requestId,
                           String entityId,
                           LiteLlmAdminClient.LiteLlmCallResult result) {
        auditLogService.logEvent(
                eventType,
                actor == null || actor.isBlank() ? "grayfile-litellm-sync" : actor,
                "litellm_resource",
                entityId,
                auditLogService.payloadOf(
                        "request_id", requestId,
                        "success", result.success(),
                        "resource_id", result.resourceId(),
                        "error", result.error(),
                        "dry_run", client.dryRun()
                ),
                Instant.now()
        );
    }

    public record ReconciliationResult(int attempted,
                                       int failed,
                                       Instant completedAt,
                                       boolean syncEnabled,
                                       boolean dryRun) {
    }
}
