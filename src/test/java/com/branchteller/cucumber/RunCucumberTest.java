package com.branchteller.cucumber;

import org.junit.platform.suite.api.ConfigurationParameter;
import org.junit.platform.suite.api.IncludeEngines;
import org.junit.platform.suite.api.SelectClasspathResource;
import org.junit.platform.suite.api.Suite;

import static io.cucumber.junit.platform.engine.Constants.GLUE_PROPERTY_NAME;
import static io.cucumber.junit.platform.engine.Constants.PLUGIN_PROPERTY_NAME;

/**
 * Entry point that lets `mvn test` (via the JUnit Platform Suite engine) discover and
 * run every .feature file under src/test/resources/features, alongside the ordinary
 * @Test classes, in the same build. Produces a human-readable HTML report
 * (target/cucumber-report/cucumber.html), a machine-readable JSON report (for CI
 * artifacts/other tooling), and feeds the same Allure results directory the JUnit5
 * tests write to, so `mvn allure:report` covers both kinds of tests in one place.
 *
 * <p>{@code failIfNoTests = false}: when Maven Surefire is invoked with {@code -Dgroups=...}
 * (e.g. {@code -Dgroups=web-automation} to run just the opt-in Selenium suite), that tag
 * filter is inherited by every nested engine this suite delegates to -- including Cucumber's.
 * None of the .feature scenarios carry that tag, so the suite legitimately discovers zero
 * tests in that situation. Without this flag the JUnit Platform Suite Engine treats "zero
 * tests discovered" as a hard configuration error (NoTestsDiscoveredException) and fails the
 * whole build, even though nothing is actually broken -- see
 * https://github.com/junit-team/junit-framework/discussions/4100.</p>
 */
@Suite(failIfNoTests = false)
@IncludeEngines("cucumber")
@SelectClasspathResource("features")
@ConfigurationParameter(key = GLUE_PROPERTY_NAME, value = "com.branchteller.cucumber.steps")
@ConfigurationParameter(key = PLUGIN_PROPERTY_NAME, value =
        "pretty, " +
        "html:target/cucumber-report/cucumber.html, " +
        "json:target/cucumber-report/cucumber.json, " +
        "io.qameta.allure.cucumber7jvm.AllureCucumber7Jvm")
public class RunCucumberTest {
}
