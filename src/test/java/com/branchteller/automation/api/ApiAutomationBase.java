package com.branchteller.automation.api;

import com.branchteller.support.TestApiServer;
import io.restassured.RestAssured;
import org.junit.jupiter.api.BeforeAll;

/**
 * Shared setup for the RestAssured-based API automation suite. Starts the same
 * in-process ApiServer + H2 schema that {@code ApiServerIntegrationTest} and the
 * Cucumber {@code api.feature} steps use (see {@link TestApiServer}), then points
 * RestAssured's default request spec at it.
 *
 * <p>Kept separate from {@code ApiServerIntegrationTest} (which drives the raw JDK
 * {@code HttpClient}) so the same endpoints are also exercised through an
 * industry-standard REST automation library -- given/when/then syntax, JSON path
 * matchers, fluent assertions -- the style most API test automation portfolios and
 * interviews expect, on top of (not instead of) the plain-Java integration tests.</p>
 */
public abstract class ApiAutomationBase {

    @BeforeAll
    static void configureRestAssured() throws Exception {
        int port = TestApiServer.ensureStarted();
        RestAssured.baseURI = "http://localhost";
        RestAssured.port = port;
    }
}
