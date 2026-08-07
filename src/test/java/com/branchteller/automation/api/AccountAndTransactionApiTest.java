package com.branchteller.automation.api;

import com.branchteller.support.TestDatabase;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;

/**
 * RestAssured coverage for the account-lookup and deposit/withdraw endpoints,
 * including a full deposit-then-lookup round trip through real HTTP against the
 * shared H2-backed ApiServer instance (see {@link ApiAutomationBase}). Each test
 * seeds its own account via {@link TestDatabase#standardFixture(BigDecimal)} so
 * these can run in any order, in parallel with the other automation/unit suites,
 * without colliding on state.
 */
@Tag("api-automation")
class AccountAndTransactionApiTest extends ApiAutomationBase {

    @Test
    void accountLookup_knownAccount_returnsBalanceAndActiveStatus() throws Exception {
        TestDatabase.Fixture fx = TestDatabase.standardFixture(new BigDecimal("500.00"));
        String accountNumber = TestDatabase.accountNumberFor(fx.accountId);

        given()
        .when()
            .get("/api/accounts/{accountNumber}", accountNumber)
        .then()
            .statusCode(200)
            .body("accountNumber", equalTo(accountNumber))
            .body("status", equalTo("ACTIVE"))
            .body("balance", equalTo("500.00"));
    }

    @Test
    void accountLookup_unknownAccount_returns404WithErrorBody() {
        given()
        .when()
            .get("/api/accounts/{accountNumber}", "DOES-NOT-EXIST-999")
        .then()
            .statusCode(404)
            .body("error", containsString("Account not found"));
    }

    @Test
    void deposit_validRequest_returns201AndUpdatesBalance() throws Exception {
        TestDatabase.Fixture fx = TestDatabase.standardFixture(new BigDecimal("100.00"));
        String accountNumber = TestDatabase.accountNumberFor(fx.accountId);

        Map<String, Object> requestBody = Map.of(
                "accountNumber", accountNumber,
                "amount", 75,
                "tellerId", fx.tellerId,
                "note", "RestAssured automation deposit"
        );

        given()
            .contentType("application/json")
            .body(requestBody)
        .when()
            .post("/api/transactions/deposit")
        .then()
            .statusCode(201)
            .body("type", equalTo("DEPOSIT"))
            .body("balanceAfter", equalTo("175.00"));
    }

    @Test
    void withdraw_amountExceedingBalance_returns422InsufficientFunds() throws Exception {
        TestDatabase.Fixture fx = TestDatabase.standardFixture(new BigDecimal("20.00"));
        String accountNumber = TestDatabase.accountNumberFor(fx.accountId);

        Map<String, Object> requestBody = Map.of(
                "accountNumber", accountNumber,
                "amount", 9999,
                "tellerId", fx.tellerId
        );

        given()
            .contentType("application/json")
            .body(requestBody)
        .when()
            .post("/api/transactions/withdraw")
        .then()
            .statusCode(422);
    }

    @Test
    void deposit_unknownAccount_returns404() {
        Map<String, Object> requestBody = Map.of(
                "accountNumber", "NOPE-123",
                "amount", 10,
                "tellerId", 1
        );

        given()
            .contentType("application/json")
            .body(requestBody)
        .when()
            .post("/api/transactions/deposit")
        .then()
            .statusCode(404);
    }

    @Test
    void depositThenLookup_reflectsTheSameBalance_endToEnd() throws Exception {
        TestDatabase.Fixture fx = TestDatabase.standardFixture(new BigDecimal("0.00"));
        String accountNumber = TestDatabase.accountNumberFor(fx.accountId);

        given()
            .contentType("application/json")
            .body(Map.of("accountNumber", accountNumber, "amount", 250, "tellerId", fx.tellerId))
        .when()
            .post("/api/transactions/deposit")
        .then()
            .statusCode(201);

        given()
        .when()
            .get("/api/accounts/{accountNumber}", accountNumber)
        .then()
            .statusCode(200)
            .body("balance", equalTo("250.00"));
    }
}
