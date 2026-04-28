package io.grayfile.api;

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

import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;

@QuarkusTest
class ManagementAuditResourceTest {

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
        try {
            billingWindowRepository.deleteAll();
            usageEventRepository.deleteAll();
            auditLogRepository.deleteAll();
            auditExportStateRepository.deleteAll();
            apiKeyRepository.deleteAll();
            llmModelRepository.deleteAll();
            customerRepository.deleteAll();
            userTransaction.commit();
        } catch (Exception exception) {
            userTransaction.rollback();
            throw exception;
        }
    }

    @Test
    void shouldPersistAuditMetadataAndStateTransitions() {
        given()
                .contentType("application/json")
                .header("x-actor-id", "alice")
                .header("x-source-ip", "10.1.2.3")
                .header("x-request-id", "req-mgmt-1")
                .header("x-change-reason", "initial onboarding")
                .body(Map.of("id", "customer-1", "name", "Acme"))
                .when()
                .post("/management/v1/customers")
                .then()
                .statusCode(201);

        given()
                .queryParam("type", "CUSTOMER_CREATED")
                .queryParam("entityType", "customer")
                .queryParam("entityId", "customer-1")
                .when()
                .get("/management/v1/audit-events")
                .then()
                .statusCode(200)
                .body("$", hasSize(1))
                .body("[0].payload.actor_id", equalTo("alice"))
                .body("[0].payload.source_ip", equalTo("10.1.2.3"))
                .body("[0].payload.request_id", equalTo("req-mgmt-1"))
                .body("[0].payload.reason", equalTo("initial onboarding"))
                .body("[0].payload.old_state.size()", equalTo(0))
                .body("[0].payload.new_state.id", equalTo("customer-1"));
    }

    @Test
    void shouldExposeFilterableAuditReadEndpoint() {
        given()
                .contentType("application/json")
                .body(Map.of("id", "customer-1", "name", "Acme"))
                .when()
                .post("/management/v1/customers")
                .then()
                .statusCode(201);

        given()
                .contentType("application/json")
                .body(Map.of("id", "key-1", "customerId", "customer-1", "name", "Primary"))
                .when()
                .post("/management/v1/api-keys")
                .then()
                .statusCode(201);

        given()
                .queryParam("type", "API_KEY_CREATED")
                .queryParam("entityType", "api_key")
                .queryParam("entityId", "key-1")
                .queryParam("limit", 10)
                .when()
                .get("/management/v1/audit-events")
                .then()
                .statusCode(200)
                .body("$", hasSize(1))
                .body("[0].eventType", equalTo("API_KEY_CREATED"))
                .body("[0].entityId", equalTo("key-1"));
    }

    @Test
    void shouldEmitCriticalAlertsForSensitiveEvents() {
        given()
                .contentType("application/json")
                .body(Map.of("id", "customer-1", "name", "Acme"))
                .when()
                .post("/management/v1/customers")
                .then()
                .statusCode(201);

        given()
                .contentType("application/json")
                .body(Map.of("id", "gpt-4o-mini", "displayName", "GPT-4o Mini", "provider", "openai"))
                .when()
                .post("/management/v1/models")
                .then()
                .statusCode(201);

        given()
                .contentType("application/json")
                .body(Map.of("displayName", "GPT-4o Mini", "provider", "openai", "active", false, "changeType", "routing"))
                .header("x-bulk-change-size", "50")
                .when()
                .put("/management/v1/models/gpt-4o-mini")
                .then()
                .statusCode(200);

        given()
                .queryParam("type", "CRITICAL_ALERT_TRIGGERED")
                .queryParam("entityType", "model")
                .queryParam("entityId", "gpt-4o-mini")
                .when()
                .get("/management/v1/audit-events")
                .then()
                .statusCode(200)
                .body("$.size()", greaterThan(0))
                .body("payload.alert_type", hasItem("ACTIVE_MODEL_DISABLED"))
                .body("payload.alert_type", hasItem("MASS_ROUTING_CHANGE"));

        given()
                .contentType("application/json")
                .body(Map.of("id", "key-1", "customerId", "customer-1", "name", "Primary"))
                .when()
                .post("/management/v1/api-keys")
                .then()
                .statusCode(201);

        given()
                .when()
                .delete("/management/v1/api-keys/key-1")
                .then()
                .statusCode(200);

        given()
                .queryParam("type", "CRITICAL_ALERT_TRIGGERED")
                .queryParam("entityType", "api_key")
                .queryParam("entityId", "key-1")
                .when()
                .get("/management/v1/audit-events")
                .then()
                .statusCode(200)
                .body("$.size()", greaterThan(0))
                .body("payload.alert_type", hasItem("API_KEY_REMOVED"));
    }
}
