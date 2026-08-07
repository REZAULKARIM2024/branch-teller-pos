package com.branchteller.automation.web.pages;

import org.openqa.selenium.WebDriver;

/**
 * Page Object for the app shell (frontend/src/App.tsx) -- the header, health banner,
 * and the three-tab nav that switches between Teller Operations / Customers /
 * General Ledger. Individual tab content is modeled by its own page object
 * (see {@link TellerOperationsPage}); the other two tabs don't yet need one since
 * the flow tests only assert against their raw table markup.
 */
public class BranchTellerAppPage extends BasePage {

    public BranchTellerAppPage(WebDriver driver) {
        super(driver);
    }

    public BranchTellerAppPage openTellerOperationsTab() {
        byTestId("tab-accounts").click();
        return this;
    }

    public BranchTellerAppPage openCustomersTab() {
        byTestId("tab-customers").click();
        return this;
    }

    public BranchTellerAppPage openLedgerTab() {
        byTestId("tab-ledger").click();
        return this;
    }

    /** Waits for the API/DB health strip to render at all (up or down) -- a signal
     *  the page has finished its first render and the app shell is interactive. */
    public BranchTellerAppPage waitForHealthBanner() {
        byTestId("health-banner");
        return this;
    }
}
