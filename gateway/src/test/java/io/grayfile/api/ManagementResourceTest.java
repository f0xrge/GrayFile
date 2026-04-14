package io.grayfile.api;

import io.grayfile.domain.ApiKeyEntity;
import io.grayfile.domain.BillingWindowEntity;
import io.grayfile.domain.CustomerEntity;
import io.grayfile.domain.LlmModelEntity;
import io.grayfile.domain.UsageEventEntity;
import io.grayfile.persistence.ApiKeyRepository;
import io.grayfile.persistence.AuditExportStateRepository;
import io.grayfile.persistence.AuditLogRepository;
import io.grayfile.persistence.BillingWindowRepository;
import io.grayfile.persistence.CustomerRepository;
import io.grayfile.persistence.CustomerModelPricingRepository;
import io.grayfile.persistence.LlmModelRepository;
import io.grayfile.persistence.UsageEventRepository;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.UserTransaction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
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
    CustomerModelPricingRepository customerModelPricingRepository;

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
        customerModelPricingRepository.deleteAll();
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
                .body(Map.of(
                        "id", "gpt-4o-mini",
                        "displayName", "GPT-4o Mini",
                        "provider", "openai",
                        "defaultTimeCriterionSeconds", 600,
                        "defaultTimePrice", 10,
                        "defaultTokenCriterion", 1000,
                        "defaultTokenPrice", 2
                ))
                .when()
                .post("/management/v1/models")
                .then()
                .statusCode(201)
                .body("id", equalTo("gpt-4o-mini"))
                .body("provider", equalTo("openai"))
                .body("defaultTimeCriterionSeconds", equalTo(600))
                .body("defaultTimePrice", equalTo(10f))
                .body("defaultTokenCriterion", equalTo(1000))
                .body("defaultTokenPrice", equalTo(2f));

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
                .body(Map.of(
                        "id", "gpt-4o-mini",
                        "displayName", "GPT-4o Mini",
                        "provider", "openai",
                        "defaultTimeCriterionSeconds", 600,
                        "defaultTimePrice", 10,
                        "defaultTokenCriterion", 1000,
                        "defaultTokenPrice", 2
                ))
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
                .body(Map.of("backendId", "backend-b", "baseUrl", "http://backend-b:18080", "weight", 10, "active", true))
                .when()
                .post("/management/v1/models/gpt-4o-mini/routes")
                .then()
                .statusCode(201);

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

    @Test
    void shouldRejectInvalidModelRouteBaseUrl() {
        given()
                .contentType("application/json")
                .body(Map.of(
                        "id", "gpt-4o-mini",
                        "displayName", "GPT-4o Mini",
                        "provider", "openai",
                        "defaultTimeCriterionSeconds", 600,
                        "defaultTimePrice", 10,
                        "defaultTokenCriterion", 1000,
                        "defaultTokenPrice", 2
                ))
                .when()
                .post("/management/v1/models")
                .then()
                .statusCode(201);

        given()
                .contentType("application/json")
                .body(Map.of("backendId", "backend-a", "baseUrl", "backend-a:18080", "weight", 90, "active", true))
                .when()
                .post("/management/v1/models/gpt-4o-mini/routes")
                .then()
                .statusCode(400)
                .body(containsString("base url"));
    }

    @Test
    void shouldRejectUnsafeModelRouteBaseUrlHost() {
        given()
                .contentType("application/json")
                .body(Map.of(
                        "id", "gpt-4o-mini",
                        "displayName", "GPT-4o Mini",
                        "provider", "openai",
                        "defaultTimeCriterionSeconds", 600,
                        "defaultTimePrice", 10,
                        "defaultTokenCriterion", 1000,
                        "defaultTokenPrice", 2
                ))
                .when()
                .post("/management/v1/models")
                .then()
                .statusCode(201);

        given()
                .contentType("application/json")
                .body(Map.of("backendId", "backend-a", "baseUrl", "http://127.0.0.1:18080", "weight", 90, "active", true))
                .when()
                .post("/management/v1/models/gpt-4o-mini/routes")
                .then()
                .statusCode(400)
                .body(containsString("not allowed"));
    }

    @Test
    void shouldExposeCustomerPricingAndUsageAnalytics() throws Exception {
        seedManagementScope();

        given()
                .contentType("application/json")
                .body(Map.of(
                        "timeCriterionSeconds", 600,
                        "timePrice", 10,
                        "tokenCriterion", 1000,
                        "tokenPrice", 1.2,
                        "changeType", "pricing"
                ))
                .when()
                .put("/management/v1/models/gpt-4o-mini/customer-pricing/customer-1")
                .then()
                .statusCode(200)
                .body("customerId", equalTo("customer-1"))
                .body("modelId", equalTo("gpt-4o-mini"))
                .body("timeCriterionSeconds", equalTo(600))
                .body("tokenCriterion", equalTo(1000));

        persistUsageEvent(
                "customer-1",
                "key-1",
                "gpt-4o-mini",
                "req-1",
                12_000,
                400,
                100,
                500,
                new BigDecimal("30.000000"),
                new BigDecimal("0.600000"),
                new BigDecimal("30.600000")
        );
        persistUsageEvent(
                "customer-2",
                "key-3",
                "gpt-4o-mini",
                "req-2",
                6_000,
                150,
                50,
                200,
                new BigDecimal("3.000000"),
                new BigDecimal("0.100000"),
                new BigDecimal("3.100000")
        );

        given()
                .when()
                .get("/management/v1/models/gpt-4o-mini/customer-pricing")
                .then()
                .statusCode(200)
                .body("$", hasSize(1))
                .body("[0].customerId", equalTo("customer-1"));

        given()
                .when()
                .get("/management/v1/usage-analytics")
                .then()
                .statusCode(200)
                .body("summary.requestCount", equalTo(2))
                .body("summary.durationMs", equalTo(18000))
                .body("summary.totalTokens", equalTo(700))
                .body("byCustomer", hasSize(2))
                .body("byModel", hasSize(1))
                .body("byCustomerModel", hasSize(2));
    }

    @Test
    void shouldRollbackWhenDeletingLastActiveRoute() {
        given()
                .contentType("application/json")
                .body(Map.of(
                        "id", "gpt-4o-mini",
                        "displayName", "GPT-4o Mini",
                        "provider", "openai",
                        "defaultTimeCriterionSeconds", 600,
                        "defaultTimePrice", 10,
                        "defaultTokenCriterion", 1000,
                        "defaultTokenPrice", 2
                ))
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
                .statusCode(201);

        given()
                .when()
                .delete("/management/v1/models/gpt-4o-mini/routes/backend-a")
                .then()
                .statusCode(400)
                .body(containsString("at least one active route"));

        given()
                .when()
                .get("/management/v1/models/gpt-4o-mini/routes")
                .then()
                .statusCode(200)
                .body("$", hasSize(1))
                .body("[0].backendId", equalTo("backend-a"));
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
        model.defaultTimeCriterionSeconds = 600;
        model.defaultTimePrice = BigDecimal.ZERO.setScale(6);
        model.defaultTokenCriterion = 1000;
        model.defaultTokenPrice = BigDecimal.ZERO.setScale(6);
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

    private void persistUsageEvent(String customerId,
                                   String apiKeyId,
                                   String modelId,
                                   String requestId,
                                   long durationMs,
                                   int promptTokens,
                                   int completionTokens,
                                   int totalTokens,
                                   BigDecimal timeCost,
                                   BigDecimal tokenCost,
                                   BigDecimal totalCost) throws Exception {
        userTransaction.begin();
        UsageEventEntity entity = new UsageEventEntity();
        entity.id = UUID.randomUUID();
        entity.customerId = customerId;
        entity.apiKeyId = apiKeyId;
        entity.model = modelId;
        entity.requestId = requestId;
        entity.eventTime = Instant.parse("2026-04-06T00:00:00Z");
        entity.durationMs = durationMs;
        entity.promptTokens = promptTokens;
        entity.completionTokens = completionTokens;
        entity.totalTokens = totalTokens;
        entity.contractVersion = "usage_extraction.v1";
        entity.extractorVersion = "gateway-backend-payload-v1";
        entity.usageSignature = "sig";
        entity.billedTimeCriterionSeconds = 600;
        entity.billedTimePrice = BigDecimal.ZERO.setScale(6);
        entity.billedTokenCriterion = 1000;
        entity.billedTokenPrice = BigDecimal.ZERO.setScale(6);
        entity.timeCost = timeCost;
        entity.tokenCost = tokenCost;
        entity.totalCost = totalCost;
        entity.pricingSource = "customer-model";
        usageEventRepository.persist(entity);
        userTransaction.commit();
    }
}
