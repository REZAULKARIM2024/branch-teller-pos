package com.branchteller.gui;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Senior-QA-style regression coverage for {@link AboutPanel}'s "Modules" list.
 *
 * <p>QA finding (fixed): the About page used to list only 11 of the application's 25 real
 * tabs, missing Products &amp; Services, Holds, Cards, Standing Instructions, Payments,
 * Complaints, Notifications, Approvals, General Ledger, Financial Reports, Compliance, Credit
 * Scoring, Branches, and Security -- more than half the application's actual functionality was
 * simply never added as the app grew. Rather than just checking today's fixed content once,
 * this test reads {@code tab.*} from {@code messages.properties} directly (the single source of
 * truth {@link com.branchteller.i18n.Messages} itself reads from for every tab label in
 * {@code MainFrame}) and asserts every one of those labels is actually mentioned on the About
 * page. That means if a future phase adds a new tab and someone forgets to update this page
 * again, this test fails immediately instead of the omission going unnoticed indefinitely, the
 * same way the original 14-module gap did.
 *
 * <p>Deliberately a plain, fast, headless JUnit test -- no Swing component construction, no
 * display, no database -- because {@link AboutPanel#html()} is a pure static string builder
 * with no instance state, kept that way specifically so this kind of test doesn't need any of
 * that machinery.</p>
 */
class AboutContentTest {

    /** tab.help and tab.about are the About page and the Help page referring to themselves /
     *  each other -- not application "modules" in the sense this page's Modules section means,
     *  so they're deliberately excluded from the completeness check below. */
    private static final List<String> SELF_REFERENTIAL_KEYS = List.of("tab.help", "tab.about");

    @Test
    void aboutPage_mentionsEveryRealApplicationModule_regressionTest() throws IOException {
        Properties tabLabels = loadTabLabels();
        String normalizedHtml = normalize(AboutPanel.html());

        List<String> missing = new ArrayList<>();
        for (String key : tabLabels.stringPropertyNames()) {
            if (SELF_REFERENTIAL_KEYS.contains(key)) continue;
            String label = normalize(tabLabels.getProperty(key));
            if (!normalizedHtml.contains(label)) {
                missing.add(key + "=" + tabLabels.getProperty(key));
            }
        }

        assertTrue(missing.isEmpty(),
                "About page's Modules list is missing these real tabs: " + missing);
    }

    @Test
    void aboutPage_stillShowsTheBankNameAndContactInfo() {
        String html = AboutPanel.html();

        assertTrue(html.contains("NY Financial Bank"));
        assertTrue(html.contains("support@nyfinancialbank.bank"));
        assertTrue(html.contains("(212) 555-0142"));
    }

    /** Loads the real tab.* keys from the same messages.properties file Messages.tr() reads,
     *  so this test's expectations track the actual application instead of a hand-maintained
     *  duplicate list that could itself go stale. */
    private Properties loadTabLabels() throws IOException {
        Properties all = new Properties();
        try (InputStream in = getClass().getClassLoader().getResourceAsStream("i18n/messages.properties")) {
            assertNotNull(in, "Could not find i18n/messages.properties on the test classpath");
            all.load(in);
        }
        Properties tabs = new Properties();
        for (String key : all.stringPropertyNames()) {
            if (key.startsWith("tab.")) {
                tabs.setProperty(key, all.getProperty(key));
            }
        }
        assertFalse(tabs.isEmpty(), "Expected at least one tab.* key in messages.properties");
        return tabs;
    }

    /** Lowercases and un-escapes the handful of HTML entities this page's labels can contain
     *  (only "&amp;" shows up today, e.g. "Products &amp; Services"), so a raw properties-file
     *  value like "Products & Services" can be matched against the rendered HTML source. */
    private String normalize(String text) {
        return text.toLowerCase(Locale.ROOT).replace("&amp;", "&");
    }
}
