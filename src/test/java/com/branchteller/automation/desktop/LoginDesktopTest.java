package com.branchteller.automation.desktop;

import com.branchteller.gui.LoginFrame;
import org.assertj.swing.edt.GuiActionRunner;
import org.assertj.swing.fixture.FrameFixture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Drives the real Swing {@link LoginFrame} in-process via AssertJ-Swing -- the same
 * component tree the desktop app launches from {@code Main} / {@code run_app.bat},
 * not a mock or a test-only stand-in UI.
 *
 * <p>Deliberately exercises the client-side validation path (submitting with both
 * fields empty) rather than a real login, so this suite has no dependency on a live
 * MySQL connection or seeded credentials and can run anywhere a display is
 * available, independent of whatever database state is currently seeded.</p>
 *
 * <p>Opt-in -- excluded from the default {@code mvn verify} (see pom.xml's surefire
 * {@code excludedGroups}) because Swing needs a real or virtual display, which a
 * bare GitHub-hosted Linux runner doesn't have by default.</p>
 *
 * <p>Run with: {@code mvn test -Dgroups=desktop-automation}.
 * On headless Linux, wrap with Xvfb: {@code xvfb-run -a mvn test -Dgroups=desktop-automation}.</p>
 */
@Tag("desktop-automation")
class LoginDesktopTest {

    private FrameFixture window;

    @BeforeEach
    void launchLoginFrame() {
        LoginFrame frame = GuiActionRunner.execute(LoginFrame::new);
        window = new FrameFixture(frame);
        window.show();
    }

    @AfterEach
    void closeFrame() {
        if (window != null) window.cleanUp();
    }

    @Test
    void submittingWithBothFieldsEmpty_showsAWarningDialog() {
        window.button("loginButton").click();

        window.optionPane().requireWarningMessage();
        window.optionPane().okButton().click();
    }

    @Test
    void typingIntoTheUsernameAndPasswordFields_isReflectedInTheirText() {
        window.textBox("usernameField").enterText("teller1");
        window.textBox("passwordField").enterText("teller123");

        window.textBox("usernameField").requireText("teller1");
        window.textBox("passwordField").requireText("teller123");
    }
}
