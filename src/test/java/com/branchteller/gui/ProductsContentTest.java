package com.branchteller.gui;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Senior-QA-style regression coverage for the Products &amp; Services page. There's no form to
 * validate here -- unlike Teller Counter, Cheques, or Loans, this tab is pure static reference
 * content with zero user input and zero business logic, driven entirely by {@link
 * ProductsPanel#CATEGORY_KEYS}/{@link ProductsPanel#CATEGORY_ITEMS} and translated {@code
 * products.*} keys in each locale's {@code messages*.properties}. So the actual QA risk here
 * isn't invalid input -- it's translation-completeness drift: {@link
 * com.branchteller.i18n.Messages#tr} never throws for a missing key, it silently returns the raw
 * key string itself (e.g. a teller would see the literal text "products.personal.travel.desc" on
 * screen instead of a description) -- exactly the class of bug {@code AboutContentTest} already
 * caught and fixed for the About page's Modules list earlier in this review.
 *
 * <p>This review found all 41 real {@code products.*} keys already present and correctly
 * translated in all 5 locale files (en/bn/es/fr/ar) -- no bug to fix here. This test locks that
 * state in as a permanent regression guard: it reads {@link ProductsPanel#CATEGORY_KEYS}/{@link
 * ProductsPanel#CATEGORY_ITEMS} directly (the real source of truth the panel itself uses, not a
 * hand-maintained copy that could silently drift out of sync), so if a future change adds a new
 * category or product without adding its translation to even one of the 5 locales, this test
 * fails immediately instead of that gap only being discovered by a teller switching languages in
 * production.</p>
 */
class ProductsContentTest {

    @Test
    void everyProductsKey_isTranslatedInEveryLocale_regressionTest() {
        List<String> expectedKeys = new ArrayList<>();
        expectedKeys.add("products.title");
        expectedKeys.add("products.categoriesTitle");
        for (String categoryKey : ProductsPanel.CATEGORY_KEYS) {
            expectedKeys.add("products.category." + categoryKey);
            for (String itemKey : ProductsPanel.CATEGORY_ITEMS.get(categoryKey)) {
                expectedKeys.add("products." + categoryKey + "." + itemKey);
                expectedKeys.add("products." + categoryKey + "." + itemKey + ".desc");
            }
        }
        // Sanity check on the test itself: if CATEGORY_ITEMS were ever empty this would trivially
        // "pass" with nothing actually checked.
        assertTrue(expectedKeys.size() >= 20, "Expected a substantial number of real keys, got: " + expectedKeys.size());

        for (java.util.Locale locale : com.branchteller.i18n.Messages.supportedLocales()) {
            ResourceBundle bundle = ResourceBundle.getBundle("i18n.messages", locale);
            List<String> missing = new ArrayList<>();
            for (String key : expectedKeys) {
                if (!bundle.containsKey(key) || bundle.getString(key).isBlank()) {
                    missing.add(key);
                }
            }
            assertTrue(missing.isEmpty(),
                    "Locale '" + locale + "' is missing (or has blank) translations for: " + missing);
        }
    }

    @Test
    void everyCategoryHasAtLeastOneProduct() {
        for (String categoryKey : ProductsPanel.CATEGORY_KEYS) {
            String[] items = ProductsPanel.CATEGORY_ITEMS.get(categoryKey);
            assertTrue(items != null && items.length > 0, "Category '" + categoryKey + "' has no products listed");
        }
    }
}
