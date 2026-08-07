package com.branchteller.automation.web.pages;

import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.time.Duration;

/**
 * Common plumbing every page object needs: an explicit-wait handle and
 * data-testid-scoped lookup helpers (see the data-testid attributes added to the
 * React components under frontend/src/components). Page objects extend this and
 * expose only business-meaningful methods (lookupAccount(...), openLedgerTab()) to
 * their tests -- no raw Selenium By-locators leak out of this package.
 */
public abstract class BasePage {

    protected final WebDriver driver;
    protected final WebDriverWait wait;

    protected BasePage(WebDriver driver) {
        this.driver = driver;
        this.wait = new WebDriverWait(driver, Duration.ofSeconds(10));
    }

    protected WebElement byTestId(String testId) {
        return wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector("[data-testid='" + testId + "']")));
    }

    protected WebElement visibleByTestId(String testId) {
        return wait.until(ExpectedConditions.visibilityOfElementLocated(By.cssSelector("[data-testid='" + testId + "']")));
    }
}
