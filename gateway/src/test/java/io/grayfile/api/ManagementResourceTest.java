package io.grayfile.api;

import io.grayfile.persistence.ApiKeyRepository;
import io.grayfile.persistence.BillingWindowRepository;
import io.grayfile.persistence.CustomerRepository;
import io.grayfile.persistence.LlmModelRepository;
import io.grayfile.persistence.UsageEventRepository;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.UserTransaction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;

@QuarkusTest
class ManagementResourceTest {

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
    UserTransaction userTransaction;

    @BeforeEach
    void cleanDatabase() throws Exception {
        userTransaction.begin();
        billingWindowRepository.deleteAll();
        usageEventRepository.deleteAll();
        apiKeyRepository.deleteAll();
        llmModelRepository.deleteAll();
        customerRepository.deleteAll();
        userTransaction.commit();
    }

    @Test
    void shouldManageCustomersModelsAndApiKeys() {
        given()
                .contentType("application/json")
                .body(Map.of("id", "customer-1", "name", "Acme"))
                .when()
                .post("/management/v1/customers")
                .then()
                .statusCode(201)
                .body("id", equalTo("customer-1"))
                .body("name", equalTo("Acme"))
                .body("active", equalTo(true));

        given()
                .contentType("application/json")
                .body(Map.of("id", "gpt-4o-mini", "displayName", "GPT-4o Mini", "provider", "openai"))
                .when()
                .post("/management/v1/models")
                .then()
                .statusCode(201)
                .body("id", equalTo("gpt-4o-mini"))
                .body("provider", equalTo("openai"));

        given()
                .contentType("application/json")
                .body(Map.of("id", "key-1", "customerId", "customer-1", "name", "Primary key"))
                .when()
                .post("/management/v1/api-keys")
                .then()
                .statusCode(201)
                .body("id", equalTo("key-1"))
                .body("customerId", equalTo("customer-1"))
                .body("active", equalTo(true));

        given()
                .when()
                .get("/management/v1/customers")
                .then()
                .statusCode(200)
                .body("$", hasSize(1))
                .body("[0].id", equalTo("customer-1"));

        given()
                .contentType("application/json")
                .body(Map.of("name", "Acme Updated", "active", false))
                .when()
                .put("/management/v1/customers/customer-1")
                .then()
                .statusCode(200)
                .body("name", equalTo("Acme Updated"))
                .body("active", equalTo(false));

        given()
                .when()
                .delete("/management/v1/models/gpt-4o-mini")
                .then()
                .statusCode(200)
                .body("active", equalTo(false));
    }

    @Test
    void shouldRejectApiKeyCreationForUnknownCustomer() {
        given()
                .contentType("application/json")
                .body(Map.of("id", "key-404", "customerId", "missing-customer", "name", "Broken key"))
                .when()
                .post("/management/v1/api-keys")
                .then()
                .statusCode(404);
    }

    @Test
    void shouldReturnBadRequestForInvalidPayload() {
        given()
                .contentType("application/json")
                .body(Map.of("id", "customer-2", "name", " "))
                .when()
                .post("/management/v1/customers")
                .then()
                .statusCode(400);
    }
}
