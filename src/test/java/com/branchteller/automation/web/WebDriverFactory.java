package com.branchteller.automation.web;

import io.github.bonigarcia.wdm.WebDriverManager;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;

/**
 * Creates a Chrome WebDriver for the web automation suite. WebDriverManager resolves
 * and downloads the matching chromedriver binary for whatever Chrome is installed, so
 * nobody has to hand-manage a driver executable on PATH.
 */
public final class WebDriverFactory {

    private WebDriverFactory() {}

    /** Headless by default -- matches how this suite would run in CI (e.g. under
     *  xvfb-run or a headless Chrome). Pass -Dautomation.headless=false locally to
     *  watch the browser drive itself. */
    public static WebDriver newChromeDriver() {
        WebDriverManager.chromedriver().setup();

        ChromeOptions options = new ChromeOptions();
        if (!"false".equalsIgnoreCase(System.getProperty("automation.headless"))) {
            options.addArguments("--headless=new");
        }
        options.addArguments("--window-size=1400,1000");
        options.addArguments("--disable-gpu");
        options.addArguments("--no-sandbox");
        return new ChromeDriver(options);
    }
}
