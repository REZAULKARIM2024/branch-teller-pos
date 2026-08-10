package com.branchteller.gui;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Senior-QA-style unit coverage for {@link HelpPanel}'s new search/filter feature.
 *
 * <p>QA finding: HelpPanel had 27+ topics and zero way to search or filter them -- a real
 * usability gap for a help screen whose entire job is helping someone find an answer quickly.
 * The old-fashioned "scroll and scan by eye" was the only way to find a topic. Fixed by adding
 * a search box wired to {@link HelpPanel#matchingTopics}, the pure (no-Swing) filtering logic
 * under test here.
 *
 * <p>{@code matchingTopics} is deliberately kept as a standalone static method taking a plain
 * {@code Map<String, String>} rather than reaching into a live {@code HelpPanel} instance, so
 * this suite runs as a fast, headless, ordinary unit test -- no display, no AssertJ-Swing,
 * no {@code TestDatabase} -- while the actual GUI wiring (typing into the real search field and
 * seeing the real {@code JList} update) is covered separately by
 * {@code automation.desktop.HelpDesktopTest}, which needs a display.
 */
class HelpTopicFilterTest {

    /** A small fixture map standing in for HelpPanel's real 27-topic map -- large enough to
     *  exercise title matches, body-only matches, and multi-match queries, without coupling
     *  this test's assertions to the exact wording of the real help content. */
    private Map<String, String> fixtureTopics() {
        Map<String, String> topics = new LinkedHashMap<>();
        topics.put("1. Logging In", "<p>Enter your username and password to sign in.</p>");
        topics.put("24. Employees & Payroll (Admin)", "<p>Hire staff and run payroll for a date range.</p>");
        topics.put("25. Branches (Admin)", "<p>Opening a branch requires a unique routing code.</p>");
        topics.put("26. Security (All Users)", "<p>Change your password; five failed attempts locks the account.</p>");
        return topics;
    }

    @Test
    void blankQuery_returnsEveryTopicInOriginalOrder() {
        Map<String, String> topics = fixtureTopics();

        List<String> result = HelpPanel.matchingTopics(topics, "");

        assertEquals(List.copyOf(topics.keySet()), result);
    }

    @Test
    void nullQuery_isTreatedTheSameAsBlank_returnsEveryTopic() {
        Map<String, String> topics = fixtureTopics();

        List<String> result = HelpPanel.matchingTopics(topics, null);

        assertEquals(List.copyOf(topics.keySet()), result);
    }

    @Test
    void whitespaceOnlyQuery_isTreatedAsBlank_returnsEveryTopic() {
        Map<String, String> topics = fixtureTopics();

        List<String> result = HelpPanel.matchingTopics(topics, "   ");

        assertEquals(List.copyOf(topics.keySet()), result);
    }

    @Test
    void queryMatchingATitle_isCaseInsensitive_andReturnsOnlyThatTopic() {
        Map<String, String> topics = fixtureTopics();

        List<String> result = HelpPanel.matchingTopics(topics, "PAYROLL");

        assertEquals(List.of("24. Employees & Payroll (Admin)"), result);
    }

    @Test
    void queryMatchingOnlyBodyText_stillFindsTheTopic_regressionTest() {
        // "routing" never appears in the Branches topic's *title* -- only in its body -- so this
        // guards the specific gap a title-only search would have missed.
        Map<String, String> topics = fixtureTopics();

        List<String> result = HelpPanel.matchingTopics(topics, "routing code");

        assertEquals(List.of("25. Branches (Admin)"), result);
    }

    @Test
    void queryMatchingMultipleTopics_returnsAllOfThemInOriginalOrder() {
        Map<String, String> topics = fixtureTopics();

        // "password" appears in both the Logging In and Security topics' bodies.
        List<String> result = HelpPanel.matchingTopics(topics, "password");

        assertEquals(List.of("1. Logging In", "26. Security (All Users)"), result);
    }

    @Test
    void queryMatchingNothing_returnsAnEmptyList_notNullOrAnError() {
        Map<String, String> topics = fixtureTopics();

        List<String> result = HelpPanel.matchingTopics(topics, "xyz-no-such-topic-content-zzz");

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void emptyTopicsMap_neverThrows_returnsEmptyRegardlessOfQuery() {
        List<String> result = HelpPanel.matchingTopics(new LinkedHashMap<>(), "anything");

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }
}
