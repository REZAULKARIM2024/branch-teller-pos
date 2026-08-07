package com.branchteller.automation.web.pages;

import org.openqa.selenium.WebDriver;

/**
 * Page Object for the "Teller Operations" tab (frontend/src/components/AccountsPage.tsx)
 * -- account lookup plus the deposit/withdraw form, the browser equivalent of the
 * Swing app's Teller Counter tab.
 */
public class TellerOperationsPage extends BasePage {

    public TellerOperationsPage(WebDriver driver) {
        super(driver);
    }

    public TellerOperationsPage lookupAccount(String accountNumber) {
        byTestId("account-number-input").clear();
        byTestId("account-number-input").sendKeys(accountNumber);
        byTestId("lookup-button").click();
        return this;
    }

    public String lookupErrorText() {
        return visibleByTestId("lookup-error").getText();
    }

    public String balanceText() {
        return visibleByTestId("account-balance").getText();
    }
}
