package com.branchteller.api;

import com.branchteller.support.TestApiServer;
import com.branchteller.support.TestDatabase;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Real HTTP integration tests -- starts the actual ApiServer (on the API_PORT the
 * surefire environmentVariables point at, see pom.xml, so it doesn't collide with a real
 * instance a developer might have on 8082) against the shared H2 test database, and drives
 * it purely over HTTP with java.net.http.HttpClient, the same way the React frontend or
 * any external caller would. Covers routing/navigation (404 on unknown routes, 405 on
 * wrong methods), CORS, and the deposit/withdraw/lookup/trial-balance business flows.
 */
class ApiServerIntegrationTest {

    private static int port;
    private static HttpClient client;

    @BeforeAll
    static void startServerAndSchema() throws Exception {
        // Shared with the Cucumber api.feature steps -- see TestApiServer for why this
        // server is started exactly once for the whole test JVM and never stopped.
        port = TestApiServer.ensureStarted();
        client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    }

    private HttpResponse<String> get(String path) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).GET().build();
        return client.send(req, HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> post(String path, String jsonBody) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();
        return client.send(req, HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> options(String path) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .method("OPTIONS", HttpRequest.BodyPublishers.noBody())
                .build();
        return client.send(req, HttpResponse.BodyHandlers.ofString());
    }

    // ---------- smoke ----------

    @Test
    void health_returns200AndOkStatus() throws Exception {
        HttpResponse<String> res = get("/api/health");
        assertEquals(200, res.statusCode());
        assertTrue(res.body().contains("\"status\""));
    }

    @Test
    void customers_returnsAJsonArray() throws Exception {
        HttpResponse<String> res = get("/api/customers");
        assertEquals(200, res.statusCode());
        assertTrue(res.body().trim().startsWith("["));
    }

    @Test
    void trialBalance_returnsTheSameGlAccountsAsGlService() throws Exception {
        HttpResponse<String> res = get("/api/gl/trial-balance");
        assertEquals(200, res.statusCode());
        assertTrue(res.body().contains("\"code\""));
    }

    // ---------- account lookup ----------

    @Test
    void accountLookup_knownAccount_returns200WithBalance() throws Exception {
        TestDatabase.Fixture fx = TestDatabase.standardFixture(new BigDecimal("777.00"));
        String accountNumber = TestDatabase.accountNumberFor(fx.accountId);

        HttpResponse<String> res = get("/api/accounts/" + accountNumber);
        assertEquals(200, res.statusCode());
        assertTrue(res.body().contains("777"));
    }

    @Test
    void accountLookup_unknownAccount_returns404() throws Exception {
        HttpResponse<String> res = get("/api/accounts/DOES-NOT-EXIST-999");
        assertEquals(404, res.statusCode());
        assertTrue(res.body().contains("\"error\""));
    }

    @Test
    void accountLookup_transactionsSubResource_returns501NotYetImplemented() throws Exception {
        TestDatabase.Fixture fx = TestDatabase.standardFixture(BigDecimal.ZERO);
        String accountNumber = TestDatabase.accountNumberFor(fx.accountId);
        HttpResponse<String> res = get("/api/accounts/" + accountNumber + "/transactions");
        assertEquals(501, res.statusCode());
    }

    // ---------- transactions ----------

    @Test
    void deposit_validRequest_returns201AndUpdatesBalance() throws Exception {
        TestDatabase.Fixture fx = TestDatabase.standardFixture(new BigDecimal("100.00"));
        String accountNumber = TestDatabase.accountNumberFor(fx.accountId);

        String body = "{\"accountNumber\":\"" + accountNumber + "\",\"amount\":50,\"tellerId\":" + fx.tellerId + ",\"note\":\"api test\"}";
        HttpResponse<String> res = post("/api/transactions/deposit", body);

        assertEquals(201, res.statusCode());
        assertTrue(res.body().contains("\"balanceAfter\":\"150.00\""));
    }

    @Test
    void withdraw_insufficientFunds_returns422() throws Exception {
        TestDatabase.Fixture fx = TestDatabase.standardFixture(new BigDecimal("10.00"));
        String accountNumber = TestDatabase.accountNumberFor(fx.accountId);

        String body = "{\"accountNumber\":\"" + accountNumber + "\",\"amount\":9999,\"tellerId\":" + fx.tellerId + "}";
        HttpResponse<String> res = post("/api/transactions/withdraw", body);

        assertEquals(422, res.statusCode());
    }

    @Test
    void deposit_unknownAccount_returns404() throws Exception {
        String body = "{\"accountNumber\":\"NOPE-123\",\"amount\":10,\"tellerId\":1}";
        HttpResponse<String> res = post("/api/transactions/deposit", body);
        assertEquals(404, res.statusCode());
    }

    // ---------- routing / method / CORS (negative + navigation) ----------

    @Test
    void unregisteredRoute_returns404() throws Exception {
        HttpResponse<String> res = get("/api/does-not-exist");
        assertEquals(404, res.statusCode());
    }

    @Test
    void getOnDepositEndpoint_returns405MethodNotAllowed() throws Exception {
        HttpResponse<String> res = get("/api/transactions/deposit");
        assertEquals(405, res.statusCode());
    }

    @Test
    void optionsPreflight_onDeposit_returns204WithCorsHeaders() throws Exception {
        HttpResponse<String> res = options("/api/transactions/deposit");
        assertEquals(204, res.statusCode());
        assertTrue(res.headers().firstValue("Access-Control-Allow-Origin").isPresent());
        assertTrue(res.headers().firstValue("Access-Control-Allow-Methods").isPresent());
    }

    @Test
    void everyResponse_carriesCorsAllowOriginHeader() throws Exception {
        HttpResponse<String> res = get("/api/health");
        assertEquals("*", res.headers().firstValue("Access-Control-Allow-Origin").orElse(null));
    }
}
