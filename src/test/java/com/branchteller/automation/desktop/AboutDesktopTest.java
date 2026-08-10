package com.branchteller.automation.desktop;

import com.branchteller.gui.AboutPanel;
import org.assertj.swing.edt.GuiActionRunner;
import org.assertj.swing.fixture.FrameFixture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import javax.swing.JFrame;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Drives the real Swing {@link AboutPanel} in-process via AssertJ-Swing -- the same component
 * tree {@code MainFrame} hosts on the About tab -- wrapped in a plain {@link JFrame} the way
 * {@code HelpDesktopTest} wraps {@code HelpPanel}.
 *
 * <p>{@code AboutContentTest} already pins the exact content-completeness logic (every real
 * tab.* module label must appear on the page) against the pure, static
 * {@link AboutPanel#html()} builder with no Swing involved at all. This suite's only job is to
 * confirm that content actually reaches the real, on-screen {@code JEditorPane} -- i.e. that
 * {@code pane.setText(html())} is genuinely wired up -- which a pure string test can't see.</p>
 *
 * <p>Opt-in -- excluded from the default {@code mvn verify} (see pom.xml's surefire
 * {@code excludedGroups}) because Swing needs a real or virtual display, which a bare
 * GitHub-hosted Linux runner doesn't have by default.</p>
 *
 * <p>Run with: {@code mvn test -DexcludedGroups= -Dgroups=desktop-automation}.
 * On headless Linux, wrap with Xvfb:
 * {@code xvfb-run -a mvn test -DexcludedGroups= -Dgroups=desktop-automation}.</p>
 */
@Tag("desktop-automation")
class AboutDesktopTest {

    private FrameFixture window;

    @BeforeEach
    void launchAboutPanel() {
        JFrame frame = GuiActionRunner.execute(() -> {
            JFrame f = new JFrame("About");
            f.setName("aboutFrame");
            f.add(new AboutPanel());
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
    void onLaunch_theRealContentPaneActuallyShowsTheBankNameAndModules() {
        String rendered = window.textBox("aboutContentPane").text();

        assertThat(rendered).contains("NY Financial Bank");
        // Spot-check a couple of the modules this test's earlier failures proved were missing
        // before the fix, rather than every one (AboutContentTest already covers all 23
        // exhaustively against the pure html() builder) -- this just proves the real component
        // renders the same content the builder produces, not a stale or empty pane.
        assertThat(rendered).contains("Branches");
        assertThat(rendered).contains("General Ledger");
        assertThat(rendered).contains("Credit Scoring");
    }
}
