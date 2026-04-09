package io.grayfile.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.grayfile.backend.BackendClient;
import io.grayfile.domain.ApiKeyEntity;
import io.grayfile.domain.CustomerEntity;
import io.grayfile.domain.LlmModelEntity;
import io.grayfile.persistence.ApiKeyRepository;
import io.grayfile.persistence.BillingWindowRepository;
import io.grayfile.persistence.CustomerRepository;
import io.grayfile.persistence.LlmModelRepository;
import io.grayfile.persistence.UsageEventRepository;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.UserTransaction;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
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
    ApiKeyRepository apiKeyRepository;

    @Inject
    UsageEventRepository usageEventRepository;

    @Inject
    BillingWindowRepository billingWindowRepository;

    @Inject
    ObjectMapper objectMapper;

    @InjectMock
    @RestClient
    BackendClient backendClient;

    @Inject
    UserTransaction userTransaction;

    @BeforeEach
    void cleanAndSeedDatabase() throws Exception {
        reset(backendClient);
        userTransaction.begin();
        billingWindowRepository.deleteAll();
        usageEventRepository.deleteAll();
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
        userTransaction.commit();
    }

    @Test
    void shouldAcceptKnownScopeAndPersistUsage() throws Exception {
        when(backendClient.chatCompletions(anyString(), any(), any())).thenReturn(Response.ok(
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
                .header("x-request-id", equalTo("req-1"));

        assertEquals(1L, usageEventRepository.count());
        assertEquals(1L, billingWindowRepository.count());
        assertEquals(20, billingWindowRepository.listAll().getFirst().tokenTotal);
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

        verifyNoInteractions(backendClient);
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

        verifyNoInteractions(backendClient);
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
}
