package io.grayfile.service;

import io.grayfile.domain.LlmModelEntity;
import io.grayfile.domain.ModelRouteEntity;
import io.grayfile.persistence.ApiKeyRepository;
import io.grayfile.persistence.AuditLogRepository;
import io.grayfile.persistence.BillingWindowRepository;
import io.grayfile.persistence.CustomerRepository;
import io.grayfile.persistence.LlmModelRepository;
import io.grayfile.persistence.ModelRouteRepository;
import jakarta.enterprise.event.Event;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ManagementServiceRouteHealthcheckTest {

    @Mock
    CustomerRepository customerRepository;

    @Mock
    LlmModelRepository llmModelRepository;

    @Mock
    ApiKeyRepository apiKeyRepository;

    @Mock
    BillingWindowRepository billingWindowRepository;

    @Mock
    AuditLogRepository auditLogRepository;

    @Mock
    AuditLogService auditLogService;

    @Mock
    AlertService alertService;

    @Mock
    BackendHealthcheckService backendHealthcheckService;

    @Mock
    ModelRouteRepository modelRouteRepository;

    @Mock
    Event<ModelRoutesChangedEvent> modelRoutesChangedEvent;

    ManagementService managementService;

    @BeforeEach
    void setUp() {
        managementService = new ManagementService(
                customerRepository,
                llmModelRepository,
                apiKeyRepository,
                billingWindowRepository,
                auditLogRepository,
                auditLogService,
                alertService,
                backendHealthcheckService,
                modelRouteRepository,
                modelRoutesChangedEvent,
                false,
                25,
                true
        );
    }

    @Test
    void shouldCreateInactiveRouteWithoutHealthcheck() {
        stubModel();
        when(modelRouteRepository.findByModelAndBackend("gpt-4o-mini", "backend-a")).thenReturn(Optional.empty());

        ModelRouteEntity created = managementService.createModelRoute(
                "gpt-4o-mini",
                "backend-a",
                "http://backend-a:18080",
                90,
                false,
                context()
        );

        assertEquals("backend-a", created.backendId);
        assertFalse(created.active);
        verify(backendHealthcheckService, never()).verifyReachable(anyString());
        verify(modelRouteRepository).persist(any(ModelRouteEntity.class));
    }

    @Test
    void shouldRejectActiveRouteCreationWhenHealthcheckFails() {
        stubModel();
        when(modelRouteRepository.findByModelAndBackend("gpt-4o-mini", "backend-a")).thenReturn(Optional.empty());
        doThrow(new IllegalArgumentException("backend healthcheck failed"))
                .when(backendHealthcheckService).verifyReachable("http://backend-a:18080");

        assertThrows(IllegalArgumentException.class, () -> managementService.createModelRoute(
                "gpt-4o-mini",
                "backend-a",
                "http://backend-a:18080",
                90,
                true,
                context()
        ));

        verify(backendHealthcheckService).verifyReachable("http://backend-a:18080");
        verify(modelRouteRepository, never()).persist(any(ModelRouteEntity.class));
    }

    @Test
    void shouldRequireHealthcheckWhenActivatingExistingRoute() {
        ModelRouteEntity route = new ModelRouteEntity();
        route.modelId = "gpt-4o-mini";
        route.backendId = "backend-a";
        route.baseUrl = "http://backend-a:18080";
        route.weight = 90;
        route.active = false;
        route.version = 1;

        when(modelRouteRepository.findByModelAndBackend("gpt-4o-mini", "backend-a")).thenReturn(Optional.of(route));
        when(modelRouteRepository.listActiveByModel("gpt-4o-mini")).thenReturn(List.of(route));
        doAnswer(invocation -> {
            route.active = true;
            return null;
        }).when(backendHealthcheckService).verifyReachable("http://backend-a:18080");

        ModelRouteEntity activated = managementService.setModelRouteActive(
                "gpt-4o-mini",
                "backend-a",
                true,
                context()
        );

        assertTrue(activated.active);
        verify(backendHealthcheckService).verifyReachable("http://backend-a:18080");
    }

    private ManagementService.ChangeAuditContext context() {
        return new ManagementService.ChangeAuditContext(
                "alice",
                "10.0.0.1",
                "req-1",
                "route setup",
                "routing",
                "bob",
                1
        );
    }

    private void stubModel() {
        LlmModelEntity model = new LlmModelEntity();
        model.id = "gpt-4o-mini";
        model.displayName = "GPT-4o Mini";
        model.provider = "openai";
        model.active = true;
        when(llmModelRepository.findByIdOptional("gpt-4o-mini")).thenReturn(Optional.of(model));
    }
}
