package com.branchteller.cucumber.steps;

import com.branchteller.support.TestApiServer;
import com.branchteller.support.TestDatabase;
import io.cucumber.java.Before;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Step definitions for api.feature -- drives the real ApiServer purely over HTTP,
 *  the same way the React web console does. The server itself is shared with
 *  ApiServerIntegrationTest via TestApiServer (started once for the whole test JVM). */
public class ApiSteps {

    private static int port;
    private static HttpClient client;

    private HttpResponse<String> lastResponse;
    private int lastAccountId;

    @Before
    public synchronized void ensureServerRunning() throws Exception {
        port = TestApiServer.ensureStarted();
        if (client == null) {
            client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        }
    }

    @Given("a verified customer account funded with {string} for API testing")
    public void aVerifiedCustomerAccountFundedWith(String amount) throws Exception {
        TestDatabase.Fixture fx = TestDatabase.standardFixture(new BigDecimal(amount));
        lastAccountId = fx.accountId;
    }

    @When("I GET {string}")
    public void iGet(String path) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).GET().build();
        lastResponse = client.send(req, HttpResponse.BodyHandlers.ofString());
    }

    @When("I deposit {string} into that account via the API")
    public void iDepositIntoThatAccountViaTheApi(String amount) throws Exception {
        String accountNumber = TestDatabase.accountNumberFor(lastAccountId);
        String body = "{\"accountNumber\":\"" + accountNumber + "\",\"amount\":" + amount + ",\"tellerId\":1}";
        HttpRequest req = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/transactions/deposit"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        lastResponse = client.send(req, HttpResponse.BodyHandlers.ofString());
    }

    @When("I send an OPTIONS request to {string}")
    public void iSendAnOptionsRequestTo(String path) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .method("OPTIONS", HttpRequest.BodyPublishers.noBody())
                .build();
        lastResponse = client.send(req, HttpResponse.BodyHandlers.ofString());
    }

    @Then("the API response status is {int}")
    public void theApiResponseStatusIs(int expected) {
        assertEquals(expected, lastResponse.statusCode(), "Response body was: " + lastResponse.body());
    }

    @Then("the API response body contains {string}")
    public void theApiResponseBodyContains(String expected) {
        assertTrue(lastResponse.body().contains(expected));
    }

    @Then("the API response has an Access-Control-Allow-Origin header")
    public void theApiResponseHasCorsHeader() {
        assertTrue(lastResponse.headers().firstValue("Access-Control-Allow-Origin").isPresent());
    }
}
