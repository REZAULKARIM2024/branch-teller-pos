package com.branchteller.support;

import com.branchteller.api.ApiServer;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Starts exactly one ApiServer for the whole test JVM, shared by both
 * ApiServerIntegrationTest (JUnit5) and ApiSteps (Cucumber) -- starting two servers on
 * the same API_PORT (see pom.xml's surefire environmentVariables) would race on the
 * socket bind depending on which test engine runs first. The server is intentionally
 * never stopped; Surefire terminates the forked JVM once the whole test run finishes.
 */
public final class TestApiServer {

    private static final AtomicBoolean STARTED = new AtomicBoolean(false);
    private static int port;

    private TestApiServer() {}

    public static synchronized int ensureStarted() throws IOException, java.sql.SQLException {
        TestDatabase.ensureSchema();
        if (STARTED.compareAndSet(false, true)) {
            port = Integer.parseInt(System.getenv().getOrDefault("API_PORT", "18082"));
            new ApiServer().start();
        }
        return port;
    }
}
