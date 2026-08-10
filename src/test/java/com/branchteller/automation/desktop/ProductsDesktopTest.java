package com.branchteller.automation.desktop;

import com.branchteller.gui.ProductsPanel;
import org.assertj.swing.edt.GuiActionRunner;
import org.assertj.swing.fixture.FrameFixture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.Component;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Drives the real Swing {@link ProductsPanel} in-process via AssertJ-Swing -- the same component
 * tree {@code MainFrame} hosts on the Products &amp; Services tab.
 *
 * <p>{@code ProductsContentTest} already pins the exact translation-completeness logic against
 * the panel's real static data with no Swing involved at all. This suite's only job is to confirm
 * that switching categories in the real, on-screen {@code JList} actually rebuilds the product
 * grid to show that category's items -- i.e. that the selection listener is genuinely wired up --
 * which a pure content test can't see.</p>
 *
 * <p>Opt-in -- excluded from the default {@code mvn verify}. Run with: {@code mvn test
 * -DexcludedGroups= -Dgroups=desktop-automation}.</p>
 */
@Tag("desktop-automation")
class ProductsDesktopTest {

    private FrameFixture window;

    @BeforeEach
    void launchProductsPanel() {
        JFrame frame = GuiActionRunner.execute(() -> {
            JFrame f = new JFrame("Products");
            f.setName("productsFrame");
            f.add(new ProductsPanel());
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

    /** Recursively collects the text of every JLabel under a container -- there's no single
     *  text component to read for a grid of product cards, unlike HelpPanel/AboutPanel's single
     *  JEditorPane, so this walks the real Swing tree the same way a screen reader would. */
    private static List<String> collectLabelText(Component root) {
        List<String> texts = new ArrayList<>();
        if (root instanceof JLabel label) {
            texts.add(label.getText());
        }
        if (root instanceof java.awt.Container container) {
            for (Component child : container.getComponents()) {
                texts.addAll(collectLabelText(child));
            }
        }
        return texts;
    }

    @Test
    void onLaunch_defaultsToShowingThePersonalCategory() {
        JPanel grid = (JPanel) window.panel("productsGrid").target();
        List<String> texts = GuiActionRunner.execute(() -> collectLabelText(grid));

        assertThat(texts).anyMatch(t -> t.contains("Checking"));
        assertThat(texts).anyMatch(t -> t.contains("Home Loans"));
    }

    @Test
    void selectingBusinessCategory_rebuildsTheGridToBusinessProducts_andDropsPersonalOnes() {
        window.list("productsCategoryList").selectItem("Business");

        JPanel grid = (JPanel) window.panel("productsGrid").target();
        List<String> texts = GuiActionRunner.execute(() -> collectLabelText(grid));

        assertThat(texts).anyMatch(t -> t.contains("Resource Center"));
        assertThat(texts).noneMatch(t -> t.contains("Home Loans"));
    }

    @Test
    void selectingCommercialCategory_showsCommercialProducts() {
        window.list("productsCategoryList").selectItem("Commercial");

        JPanel grid = (JPanel) window.panel("productsGrid").target();
        List<String> texts = GuiActionRunner.execute(() -> collectLabelText(grid));

        assertThat(texts).anyMatch(t -> t.contains("Treasury Services"));
        assertThat(texts).anyMatch(t -> t.contains("Capital Markets Advisory"));
    }
}
