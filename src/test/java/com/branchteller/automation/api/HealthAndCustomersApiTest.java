package com.branchteller.automation.api;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.isA;

/**
 * RestAssured coverage for the read-only/navigation endpoints: health, customer
 * directory, GL trial balance, and unknown routes. Mirrors the smoke-test section of
 * {@code ApiServerIntegrationTest} but in given/when/then style.
 */
@Tag("api-automation")
class HealthAndCustomersApiTest extends ApiAutomationBase {

    @Test
    void health_reportsOkStatus() {
        given()
        .when()
            .get("/api/health")
        .then()
            .statusCode(200)
            .body("status", equalTo("ok"));
    }

    @Test
    void customers_returnsAJsonArrayOfCustomerRecords() {
        given()
        .when()
            .get("/api/customers")
        .then()
            .statusCode(200)
            .contentType("application/json; charset=utf-8")
            .body("$", isA(List.class));
    }

    @Test
    void trialBalance_includesTheCoreChartOfAccountsCodes() {
        given()
        .when()
            .get("/api/gl/trial-balance")
        .then()
            .statusCode(200)
            .body("code", hasItem("1000"))   // Cash and Cash Equivalents
            .body("code", hasItem("1100"));  // Customer Deposits Control
    }

    @Test
    void unregisteredRoute_returns404() {
        given()
        .when()
            .get("/api/does-not-exist")
        .then()
            .statusCode(404);
    }

    @Test
    void getOnDepositEndpoint_returns405MethodNotAllowed() {
        given()
        .when()
            .get("/api/transactions/deposit")
        .then()
            .statusCode(405);
    }

    @Test
    void optionsPreflight_onDeposit_returns204WithCorsHeaders() {
        given()
        .when()
            .options("/api/transactions/deposit")
        .then()
            .statusCode(204)
            .header("Access-Control-Allow-Origin", equalTo("*"))
            .header("Access-Control-Allow-Methods", org.hamcrest.Matchers.notNullValue());
    }
}
