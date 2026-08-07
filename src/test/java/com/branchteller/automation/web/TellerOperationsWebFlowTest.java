package com.branchteller.automation.web;

import com.branchteller.automation.web.pages.BranchTellerAppPage;
import com.branchteller.automation.web.pages.TellerOperationsPage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end Selenium coverage of the React web console, driven through Page Objects
 * (see the .pages sub-package) instead of raw By-locators in the test body itself.
 *
 * <p>Opt-in -- excluded from the default {@code mvn verify} (see pom.xml's surefire
 * {@code excludedGroups}) because, unlike the H2-backed API/unit suites, it needs the
 * real stack actually running: {@code run_api_server.bat} + {@code run_frontend.bat}
 * (or {@code docker compose up}) against whatever MySQL database is currently seeded,
 * plus a real Chrome browser. Assertions below deliberately stick to things that are
 * true of the already-seeded demo data (5000+ customers/accounts, the core chart of
 * accounts) or are data-independent, rather than hard-coding a specific account
 * number that would only exist in one particular environment.</p>
 *
 * <p>Run with: {@code mvn test -Dgroups=web-automation}
 * (optionally {@code -Dautomation.web.baseUrl=http://localhost:5173}
 * and/or {@code -Dautomation.headless=false} to watch it run).</p>
 */
@Tag("web-automation")
class TellerOperationsWebFlowTest {

    private static final String BASE_URL = System.getProperty("automation.web.baseUrl", "http://localhost:5173");

    private WebDriver driver;

    @BeforeEach
    void openApp() {
        driver = WebDriverFactory.newChromeDriver();
        driver.get(BASE_URL);
        new BranchTellerAppPage(driver).waitForHealthBanner();
    }

    @AfterEach
    void closeBrowser() {
        if (driver != null) driver.quit();
    }

    @Test
    void accountLookup_unknownAccount_showsAnErrorMessage() {
        new BranchTellerAppPage(driver).openTellerOperationsTab();
        TellerOperationsPage tellerOps = new TellerOperationsPage(driver).lookupAccount("DOES-NOT-EXIST-999");

        assertTrue(tellerOps.lookupErrorText().toLowerCase().contains("not found"),
                "Expected a 'not found' error message for a bogus account number");
    }

    @Test
    void customersTab_rendersTheSeededCustomerDirectory() {
        new BranchTellerAppPage(driver).openCustomersTab();

        List<WebElement> rows = waitForTableRows();
        assertFalse(rows.isEmpty(), "Expected at least one seeded customer row in the Customers table");
    }

    @Test
    void ledgerTab_rendersTheCoreChartOfAccountCodes() {
        new BranchTellerAppPage(driver).openLedgerTab();
        waitForTableRows();

        String tableText = driver.findElement(By.cssSelector("table.data-table")).getText();
        assertTrue(tableText.contains("1000"), "Expected the Cash and Cash Equivalents GL code (1000)");
        assertTrue(tableText.contains("1100"), "Expected the Customer Deposits Control GL code (1100)");
    }

    private List<WebElement> waitForTableRows() {
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
        wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector("table.data-table tbody tr")));
        return driver.findElements(By.cssSelector("table.data-table tbody tr"));
    }
}
