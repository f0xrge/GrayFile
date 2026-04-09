package io.grayfile.api;

import io.grayfile.domain.ApiKeyEntity;
import io.grayfile.domain.BillingWindowEntity;
import io.grayfile.domain.CustomerEntity;
import io.grayfile.domain.LlmModelEntity;
import io.grayfile.persistence.ApiKeyRepository;
import io.grayfile.persistence.AuditExportStateRepository;
import io.grayfile.persistence.AuditLogRepository;
import io.grayfile.persistence.BillingWindowRepository;
import io.grayfile.persistence.CustomerRepository;
import io.grayfile.persistence.LlmModelRepository;
import io.grayfile.persistence.UsageEventRepository;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.UserTransaction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.hasItems;

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
    AuditLogRepository auditLogRepository;

    @Inject
    AuditExportStateRepository auditExportStateRepository;

    @Inject
    UserTransaction userTransaction;

    @BeforeEach
    void cleanDatabase() throws Exception {
        userTransaction.begin();
        billingWindowRepository.deleteAll();
        usageEventRepository.deleteAll();
        auditLogRepository.deleteAll();
        auditExportStateRepository.deleteAll();
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

    @Test
    void shouldListBillingWindowsAndFilterByScopeAndDates() throws Exception {
        seedManagementScope();
        persistBillingWindow(
                UUID.fromString("11111111-1111-1111-1111-111111111111"),
                "customer-1",
                "key-1",
                "gpt-4o-mini",
                Instant.parse("2026-04-01T00:00:00Z"),
                Instant.parse("2026-04-01T02:00:00Z"),
                120,
                "rolled-over",
                false
        );
        persistBillingWindow(
                UUID.fromString("22222222-2222-2222-2222-222222222222"),
                "customer-1",
                "key-2",
                "gpt-4o-mini",
                Instant.parse("2026-04-03T00:00:00Z"),
                null,
                80,
                null,
                true
        );
        persistBillingWindow(
                UUID.fromString("33333333-3333-3333-3333-333333333333"),
                "customer-2",
                "key-3",
                "gpt-4o-mini",
                Instant.parse("2026-04-05T00:00:00Z"),
                Instant.parse("2026-04-05T01:00:00Z"),
                40,
                "closed",
                false
        );

        given()
                .when()
                .get("/management/v1/billing-windows")
                .then()
                .statusCode(200)
                .body("$", hasSize(3))
                .body("[0].id", equalTo("33333333-3333-3333-3333-333333333333"))
                .body("[1].id", equalTo("22222222-2222-2222-2222-222222222222"))
                .body("[2].id", equalTo("11111111-1111-1111-1111-111111111111"));

        given()
                .queryParam("customerId", "customer-1")
                .when()
                .get("/management/v1/billing-windows")
                .then()
                .statusCode(200)
                .body("$", hasSize(2))
                .body("[0].id", equalTo("22222222-2222-2222-2222-222222222222"))
                .body("[1].id", equalTo("11111111-1111-1111-1111-111111111111"));

        given()
                .queryParam("apiKeyId", "key-3")
                .when()
                .get("/management/v1/billing-windows")
                .then()
                .statusCode(200)
                .body("$", hasSize(1))
                .body("[0].id", equalTo("33333333-3333-3333-3333-333333333333"));

        given()
                .queryParam("customerId", "customer-1")
                .queryParam("apiKeyId", "key-2")
                .when()
                .get("/management/v1/billing-windows")
                .then()
                .statusCode(200)
                .body("$", hasSize(1))
                .body("[0].id", equalTo("22222222-2222-2222-2222-222222222222"));

        given()
                .queryParam("startDate", "2026-04-02T00:00:00Z")
                .queryParam("endDate", "2026-04-04T00:00:00Z")
                .when()
                .get("/management/v1/billing-windows")
                .then()
                .statusCode(200)
                .body("$", hasSize(1))
                .body("[0].id", equalTo("22222222-2222-2222-2222-222222222222"));

        given()
                .queryParam("startDate", "2026-04-02T12:00:00Z")
                .queryParam("endDate", "2026-04-06T00:00:00Z")
                .when()
                .get("/management/v1/billing-windows")
                .then()
                .statusCode(200)
                .body("$", hasSize(2))
                .body("id", hasItems(
                        "22222222-2222-2222-2222-222222222222",
                        "33333333-3333-3333-3333-333333333333"
                ));
    }

    @Test
    void shouldRejectInvalidBillingWindowDateFilters() {
        given()
                .queryParam("startDate", "not-an-instant")
                .when()
                .get("/management/v1/billing-windows")
                .then()
                .statusCode(400);

        given()
                .queryParam("startDate", "2026-04-06T00:00:00Z")
                .queryParam("endDate", "2026-04-05T00:00:00Z")
                .when()
                .get("/management/v1/billing-windows")
                .then()
                .statusCode(400);
    }


    @Test
    void shouldManageModelRoutesLifecycle() {
        given()
                .contentType("application/json")
                .body(Map.of("id", "gpt-4o-mini", "displayName", "GPT-4o Mini", "provider", "openai"))
                .when()
                .post("/management/v1/models")
                .then()
                .statusCode(201);

        given()
                .contentType("application/json")
                .body(Map.of("backendId", "backend-a", "baseUrl", "http://backend-a:18080", "weight", 90, "active", true))
                .when()
                .post("/management/v1/models/gpt-4o-mini/routes")
                .then()
                .statusCode(201)
                .body("backendId", equalTo("backend-a"))
                .body("active", equalTo(true))
                .body("weight", equalTo(90));

        given()
                .when()
                .get("/management/v1/models/gpt-4o-mini/routes")
                .then()
                .statusCode(200)
                .body("$", hasSize(1));

        given()
                .contentType("application/json")
                .body(Map.of("active", false))
                .when()
                .put("/management/v1/models/gpt-4o-mini/routes/backend-a/active")
                .then()
                .statusCode(200)
                .body("active", equalTo(false));

        given()
                .contentType("application/json")
                .body(Map.of("weight", 25))
                .when()
                .put("/management/v1/models/gpt-4o-mini/routes/backend-a/weight")
                .then()
                .statusCode(200)
                .body("weight", equalTo(25));

        given()
                .when()
                .delete("/management/v1/models/gpt-4o-mini/routes/backend-a")
                .then()
                .statusCode(204);
    }

    private void seedManagementScope() throws Exception {
        userTransaction.begin();

        CustomerEntity customerOne = new CustomerEntity();
        customerOne.id = "customer-1";
        customerOne.name = "Customer One";
        customerOne.active = true;
        customerRepository.persist(customerOne);

        CustomerEntity customerTwo = new CustomerEntity();
        customerTwo.id = "customer-2";
        customerTwo.name = "Customer Two";
        customerTwo.active = true;
        customerRepository.persist(customerTwo);

        LlmModelEntity model = new LlmModelEntity();
        model.id = "gpt-4o-mini";
        model.displayName = "GPT-4o Mini";
        model.provider = "openai";
        model.active = true;
        llmModelRepository.persist(model);

        persistApiKey("key-1", "customer-1", "Key One");
        persistApiKey("key-2", "customer-1", "Key Two");
        persistApiKey("key-3", "customer-2", "Key Three");

        userTransaction.commit();
    }

    private void persistApiKey(String id, String customerId, String name) {
        ApiKeyEntity entity = new ApiKeyEntity();
        entity.id = id;
        entity.customerId = customerId;
        entity.name = name;
        entity.active = true;
        apiKeyRepository.persist(entity);
    }

    private void persistBillingWindow(UUID id,
                                      String customerId,
                                      String apiKeyId,
                                      String model,
                                      Instant windowStart,
                                      Instant windowEnd,
                                      int tokenTotal,
                                      String closureReason,
                                      boolean active) throws Exception {
        userTransaction.begin();
        BillingWindowEntity entity = new BillingWindowEntity();
        entity.id = id;
        entity.customerId = customerId;
        entity.apiKeyId = apiKeyId;
        entity.model = model;
        entity.windowStart = windowStart;
        entity.windowEnd = windowEnd;
        entity.tokenTotal = tokenTotal;
        entity.closureReason = closureReason;
        entity.active = active;
        billingWindowRepository.persist(entity);
        userTransaction.commit();
    }
}
