package io.grayfile.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.grayfile.backend.BackendGateway;
import io.grayfile.domain.ApiKeyEntity;
import io.grayfile.domain.CustomerEntity;
import io.grayfile.domain.LlmModelEntity;
import io.grayfile.domain.ModelRouteEntity;
import io.grayfile.persistence.ApiKeyRepository;
import io.grayfile.persistence.AuditExportStateRepository;
import io.grayfile.persistence.AuditLogRepository;
import io.grayfile.persistence.BillingWindowRepository;
import io.grayfile.persistence.CustomerRepository;
import io.grayfile.persistence.LlmModelRepository;
import io.grayfile.persistence.ModelRouteRepository;
import io.grayfile.persistence.UsageEventRepository;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.UserTransaction;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@QuarkusTest
class LlmProxyResourceTest {

    @Inject
    CustomerRepository customerRepository;

    @Inject
    LlmModelRepository llmModelRepository;

    @Inject
    ModelRouteRepository modelRouteRepository;

    @Inject
    ApiKeyRepository apiKeyRepository;

    @Inject
    UsageEventRepository usageEventRepository;

    @Inject
    BillingWindowRepository billingWindowRepository;

    @Inject
    AuditLogRepository auditLogRepository;

    @Inject
    AuditExportStateRepository auditExportStateRepository;

    @Inject
    ObjectMapper objectMapper;

    @InjectMock
    BackendGateway backendGateway;

    @Inject
    UserTransaction userTransaction;

    @BeforeEach
    void cleanAndSeedDatabase() throws Exception {
        reset(backendGateway);
        userTransaction.begin();
        billingWindowRepository.deleteAll();
        usageEventRepository.deleteAll();
        auditLogRepository.deleteAll();
        auditExportStateRepository.deleteAll();
        modelRouteRepository.deleteAll();
        apiKeyRepository.deleteAll();
        llmModelRepository.deleteAll();
        customerRepository.deleteAll();

        CustomerEntity customer = new CustomerEntity();
        customer.id = "customer-1";
        customer.name = "Acme";
        customer.active = true;
        customerRepository.persist(customer);

        LlmModelEntity model = new LlmModelEntity();
        model.id = "gpt-4o-mini";
        model.displayName = "GPT-4o Mini";
        model.provider = "openai";
        model.active = true;
        llmModelRepository.persist(model);

        ApiKeyEntity apiKey = new ApiKeyEntity();
        apiKey.id = "key-1";
        apiKey.customerId = "customer-1";
        apiKey.name = "Primary";
        apiKey.active = true;
        apiKeyRepository.persist(apiKey);

        persistRoute("gpt-4o-mini", "backend-a", "http://backend-a:18080", 100, true);
        userTransaction.commit();
    }

    @Test
    void shouldAcceptKnownScopeAndPersistUsage() throws Exception {
        when(backendGateway.proxy(anyString(), any())).thenReturn(Response.ok(
                objectMapper.readTree("""
                        {
                          "id": "resp-1",
                          "model": "gpt-4o-mini",
                          "usage": {
                            "prompt_tokens": 12,
                            "completion_tokens": 8,
                            "total_tokens": 20
                          }
                        }
                        """)
        ).build());

        given()
                .contentType("application/json")
                .header("x-customer-id", "customer-1")
                .header("x-api-key-id", "key-1")
                .header("x-request-id", "req-1")
                .body(Map.of(
                        "model", "gpt-4o-mini",
                        "messages", new Object[]{
                                Map.of("role", "user", "content", "Hello")
                        }
                ))
                .when()
                .post("/llm/v1/chat/completions")
                .then()
                .statusCode(200)
                .body("id", equalTo("resp-1"))
                .body("usage.total_tokens", equalTo(20))
                .header("x-request-id", equalTo("req-1"))
                .header("x-backend-id", equalTo("backend-a"))
                .header("x-grayfile-usage-contract-version", equalTo("usage_extraction.v1"))
                .header("x-grayfile-usage-extractor-version", equalTo("gateway-backend-payload-v1"));

        assertEquals(1L, usageEventRepository.count());
        assertEquals(1L, billingWindowRepository.count());
        assertEquals(20, billingWindowRepository.listAll().getFirst().tokenTotal);
    }


    @Test
    void shouldFlagDivergenceBetweenEdgeExtractionAndBackendPayload() throws Exception {
        when(backendGateway.proxy(anyString(), any())).thenReturn(Response.ok(
                objectMapper.readTree("""
                        {
                          "id": "resp-divergence",
                          "model": "gpt-4o-mini",
                          "usage": {
                            "prompt_tokens": 12,
                            "completion_tokens": 8,
                            "total_tokens": 20
                          }
                        }
                        """)
        )
                .header("x-edge-usage-prompt-tokens", "10")
                .header("x-edge-usage-completion-tokens", "8")
                .header("x-edge-usage-total-tokens", "18")
                .build());

        given()
                .contentType("application/json")
                .header("x-customer-id", "customer-1")
                .header("x-api-key-id", "key-1")
                .header("x-request-id", "req-divergence")
                .body(Map.of("model", "gpt-4o-mini"))
                .when()
                .post("/llm/v1/chat/completions")
                .then()
                .statusCode(200)
                .header("x-grayfile-usage-divergence", equalTo("edge_backend_mismatch"))
                .header("x-grayfile-usage-capture", equalTo("edge_backend_divergence"));

        assertEquals(0L, usageEventRepository.count());
        assertEquals(0L, billingWindowRepository.count());
    }

    @Test
    void shouldFallbackToSecondBackendWhenFirstReturnsFiveHundred() throws Exception {
        userTransaction.begin();
        persistRoute("gpt-4o-mini", "backend-b", "http://backend-b:18080", 1, true);
        userTransaction.commit();

        when(backendGateway.proxy(eq("http://backend-a:18080"), any()))
                .thenReturn(Response.serverError().entity(objectMapper.readTree("""
                        {"error":"boom"}
                        """)).build());
        when(backendGateway.proxy(eq("http://backend-b:18080"), any()))
                .thenReturn(Response.ok(objectMapper.readTree("""
                        {"id":"resp-2","model":"gpt-4o-mini","usage":{"prompt_tokens":1,"completion_tokens":1,"total_tokens":2}}
                        """)).build());

        given()
                .contentType("application/json")
                .header("x-customer-id", "customer-1")
                .header("x-api-key-id", "key-1")
                .header("x-request-id", "req-2")
                .body(Map.of("model", "gpt-4o-mini"))
                .when()
                .post("/llm/v1/chat/completions")
                .then()
                .statusCode(200)
                .header("x-backend-id", equalTo("backend-b"));
    }

    @Test
    void shouldWriteUsageExtractionAuditWhenUsageFieldsAreMissing() throws Exception {
        when(backendGateway.proxy(anyString(), any())).thenReturn(Response.ok(
                objectMapper.readTree("""
                        {
                          "id": "resp-no-usage",
                          "model": "gpt-4o-mini"
                        }
                        """)
        ).build());

        given()
                .contentType("application/json")
                .header("x-customer-id", "customer-1")
                .header("x-api-key-id", "key-1")
                .header("x-request-id", "req-no-usage")
                .body(Map.of("model", "gpt-4o-mini"))
                .when()
                .post("/llm/v1/chat/completions")
                .then()
                .statusCode(200)
                .header("x-grayfile-usage-capture", equalTo("missing_usage"));

        assertEquals(0L, usageEventRepository.count());
        assertTrue(auditLogRepository.listFiltered("USAGE_EXTRACTION_AUDIT", null, null, "usage_extraction", "req-no-usage", 10).size() >= 1);
    }

    @Test
    void shouldRejectApiKeyThatDoesNotBelongToCustomer() {
        persistForeignApiKey();

        given()
                .contentType("application/json")
                .header("x-customer-id", "customer-1")
                .header("x-api-key-id", "key-foreign")
                .body(Map.of("model", "gpt-4o-mini"))
                .when()
                .post("/llm/v1/chat/completions")
                .then()
                .statusCode(400)
                .body(equalTo("api key key-foreign does not belong to customer customer-1"));

        verifyNoInteractions(backendGateway);
    }

    @Test
    void shouldRejectMissingModel() {
        given()
                .contentType("application/json")
                .header("x-customer-id", "customer-1")
                .header("x-api-key-id", "key-1")
                .body(Map.of("messages", new Object[0]))
                .when()
                .post("/llm/v1/chat/completions")
                .then()
                .statusCode(400)
                .body(equalTo("request body must contain a non-empty model"));

        verifyNoInteractions(backendGateway);
    }

    void persistForeignApiKey() {
        try {
            userTransaction.begin();
            CustomerEntity otherCustomer = new CustomerEntity();
            otherCustomer.id = "customer-2";
            otherCustomer.name = "Other";
            otherCustomer.active = true;
            customerRepository.persist(otherCustomer);

            ApiKeyEntity foreignKey = new ApiKeyEntity();
            foreignKey.id = "key-foreign";
            foreignKey.customerId = "customer-2";
            foreignKey.name = "Foreign";
            foreignKey.active = true;
            apiKeyRepository.persist(foreignKey);
            userTransaction.commit();
        } catch (Exception exception) {
            throw new RuntimeException(exception);
        }
    }

    void persistRoute(String modelId, String backendId, String baseUrl, int weight, boolean active) {
        ModelRouteEntity route = new ModelRouteEntity();
        route.modelId = modelId;
        route.backendId = backendId;
        route.baseUrl = baseUrl;
        route.weight = weight;
        route.active = active;
        route.version = 1;
        route.updatedAt = Instant.now();
        modelRouteRepository.persist(route);
    }
}
