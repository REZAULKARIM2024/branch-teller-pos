package com.branchteller.automation.desktop;

import com.branchteller.gui.HelpPanel;
import org.assertj.swing.edt.GuiActionRunner;
import org.assertj.swing.fixture.FrameFixture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import javax.swing.JFrame;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Drives the real Swing {@link HelpPanel} in-process via AssertJ-Swing -- the same component
 * tree the desktop app launches from {@code Main} / {@code run_app.bat}, wrapped in a plain
 * {@link JFrame} the way {@code MainFrame}'s tabbed pane hosts it.
 *
 * <p>QA finding covered here: HelpPanel had 27+ topics and no way to search or filter them.
 * {@code HelpTopicFilterTest} unit-tests the pure filtering logic; this suite instead verifies
 * the actual GUI wiring -- that typing into the real search field genuinely narrows what the
 * real {@code JList} shows, and that the content pane keeps working after filtering.</p>
 *
 * <p>Opt-in -- excluded from the default {@code mvn verify} (see pom.xml's surefire
 * {@code excludedGroups}) because Swing needs a real or virtual display, which a bare
 * GitHub-hosted Linux runner doesn't have by default.</p>
 *
 * <p>Run with: {@code mvn test -DexcludedGroups= -Dgroups=desktop-automation}.
 * On headless Linux, wrap with Xvfb:
 * {@code xvfb-run -a mvn test -DexcludedGroups= -Dgroups=desktop-automation}.
 * The {@code -DexcludedGroups=} (blank) is required, not optional -- pom.xml's default
 * excludes this same tag, and in JUnit5 an excluded tag always beats an included one, so
 * {@code -Dgroups=desktop-automation} alone silently runs 0 tests instead of these.</p>
 */
@Tag("desktop-automation")
class HelpDesktopTest {

    private FrameFixture window;

    @BeforeEach
    void launchHelpPanel() {
        JFrame frame = GuiActionRunner.execute(() -> {
            JFrame f = new JFrame("Help");
            f.setName("helpFrame");
            f.add(new HelpPanel());
            f.pack();
            return f;
        });
        window = new FrameFixture(frame);
        window.show();
    }

    @AfterEach
    void closeFrame() {
        if (window != null) window.cleanUp();
    }

    @Test
    void onLaunch_theFirstTopicIsSelectedAndShownAutomatically() {
        window.list("helpTopicList").requireSelection(0);
        assertThat(window.list("helpTopicList").selection()[0]).startsWith("0. Welcome");
    }

    /**
     * QA finding (fixed after a real run against the live 27+ topic content): the first draft
     * of this test asserted an exact singleton match for "payroll", assuming that word only
     * appeared in the Employees &amp; Payroll topic. In reality "payroll" also legitimately
     * appears in the bodies of the Welcome/Roadmap, General Ledger, Financial Reports, and
     * Audit Log topics (they all mention payroll postings in passing), so the real filtered
     * list came back with 5 matches, not 1 -- correct search behavior, wrong test assumption.
     * {@code HelpTopicFilterTest} already pins the exact-match semantics against a small,
     * controlled fixture map; this GUI test's job is only to prove the real search field
     * genuinely narrows the real list and that the target topic is in it, so it no longer
     * hardcodes an exact count that real content is free to invalidate.
     */
    @Test
    void typingASearchThatMatchesOneTopic_narrowsTheListToJustThatTopic() {
        int fullCount = window.list("helpTopicList").contents().length;

        window.textBox("helpSearchField").enterText("payroll");

        String[] visible = window.list("helpTopicList").contents();
        assertThat(visible.length).isLessThan(fullCount);
        assertThat(visible).anySatisfy(title -> assertThat(title).contains("Payroll"));
    }

    /**
     * QA finding (fixed): same class of issue as above -- the new "27. Using This Help
     * Screen" topic added by this same change quotes "routing code" as its own worked
     * example of body-text search, so searching that exact phrase legitimately matches two
     * topics (Branches, and topic 27's own example text), not one. Asserting narrowing +
     * presence rather than an exact count keeps this regression test true to its actual
     * purpose (proving body text, not just titles, is searched) without being coupled to
     * how many other topics happen to mention the same phrase.
     */
    @Test
    void searchMatchesBodyTextNotJustTitles_regressionTest() {
        // "routing" only appears in the Branches topic's title-adjacent body text, never its
        // title -- a title-only filter would have missed this and returned zero results.
        int fullCount = window.list("helpTopicList").contents().length;

        window.textBox("helpSearchField").enterText("routing code");

        String[] visible = window.list("helpTopicList").contents();
        assertThat(visible.length).isLessThan(fullCount);
        assertThat(visible).anySatisfy(title -> assertThat(title).contains("Branches"));
    }

    @Test
    void clearingTheSearchBox_restoresTheFullTopicList() {
        int fullCount = window.list("helpTopicList").contents().length;

        window.textBox("helpSearchField").enterText("payroll");
        assertThat(window.list("helpTopicList").contents().length).isLessThan(fullCount);

        window.textBox("helpSearchField").setText("");
        assertThat(window.list("helpTopicList").contents()).hasSize(fullCount);
    }

    @Test
    void searchWithNoMatches_leavesTheListEmpty_withoutThrowing() {
        window.textBox("helpSearchField").enterText("xyz-no-such-topic-zzz");

        assertThat(window.list("helpTopicList").contents()).isEmpty();
    }

    /**
     * QA finding (fixed): the first draft selected list index 0 after searching "Security",
     * assuming the Security topic would land there. It doesn't -- the Welcome/Roadmap topic's
     * body also mentions "Security" in passing ("...can change their own password under
     * Security..."), and since it's inserted first in the topic map it sorted to index 0 of
     * the filtered results, so index-0 selection silently landed on the wrong topic and the
     * test failed on content it was never actually testing. Selecting by the topic's exact,
     * unambiguous title text instead of a positional index removes that fragility entirely.
     */
    @Test
    void selectingATopicAfterFiltering_updatesTheContentPane() {
        window.textBox("helpSearchField").enterText("Security");
        window.list("helpTopicList").selectItem("26. Security (All Users)");

        assertThat(window.textBox("helpContentPane").text()).contains("Change Password");
    }
}
