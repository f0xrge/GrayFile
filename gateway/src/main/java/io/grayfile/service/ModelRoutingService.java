package io.grayfile.service;

import io.grayfile.domain.ModelRouteEntity;
import io.grayfile.persistence.ModelRouteRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

@ApplicationScoped
public class ModelRoutingService {

    private final ModelRouteRepository modelRouteRepository;
    private final long cacheTtlMillis;
    private final Map<String, CachedRoutes> cache = new ConcurrentHashMap<>();

    public ModelRoutingService(ModelRouteRepository modelRouteRepository,
                               @ConfigProperty(name = "grayfile.routing.cache.ttl", defaultValue = "PT5S") Duration cacheTtl) {
        this.modelRouteRepository = modelRouteRepository;
        this.cacheTtlMillis = Math.max(1L, cacheTtl.toMillis());
    }

    public List<RouteTarget> resolveCandidates(String modelId) {
        List<ModelRouteEntity> activeRoutes = activeRoutes(modelId);
        if (activeRoutes.isEmpty()) {
            return List.of();
        }

        int selectedIndex = weightedIndex(activeRoutes);
        List<RouteTarget> ordered = new ArrayList<>(activeRoutes.size());
        ordered.add(toTarget(activeRoutes.get(selectedIndex)));
        for (int index = 0; index < activeRoutes.size(); index++) {
            if (index == selectedIndex) {
                continue;
            }
            ordered.add(toTarget(activeRoutes.get(index)));
        }
        return ordered;
    }

    public List<ModelRouteEntity> listRoutes(String modelId) {
        return modelRouteRepository.listByModel(modelId);
    }

    public void invalidateModel(String modelId) {
        cache.remove(modelId);
    }

    void onRoutesChanged(@Observes ModelRoutesChangedEvent event) {
        invalidateModel(event.modelId());
    }

    private List<ModelRouteEntity> activeRoutes(String modelId) {
        CachedRoutes entry = cache.get(modelId);
        Instant now = Instant.now();
        if (entry != null && Duration.between(entry.loadedAt(), now).toMillis() <= cacheTtlMillis) {
            return entry.routes();
        }
        List<ModelRouteEntity> loaded = modelRouteRepository.listActiveByModel(modelId);
        cache.put(modelId, new CachedRoutes(loaded, now));
        return loaded;
    }

    private int weightedIndex(List<ModelRouteEntity> routes) {
        int totalWeight = routes.stream().mapToInt(route -> Math.max(route.weight, 1)).sum();
        int needle = ThreadLocalRandom.current().nextInt(totalWeight);
        int offset = 0;
        for (int index = 0; index < routes.size(); index++) {
            offset += Math.max(routes.get(index).weight, 1);
            if (needle < offset) {
                return index;
            }
        }
        return 0;
    }

    private RouteTarget toTarget(ModelRouteEntity route) {
        return new RouteTarget(route.backendId, route.baseUrl, route.weight, route.version);
    }

    public record RouteTarget(String backendId, String baseUrl, int weight, int version) {
    }

    private record CachedRoutes(List<ModelRouteEntity> routes, Instant loadedAt) {
    }
}
