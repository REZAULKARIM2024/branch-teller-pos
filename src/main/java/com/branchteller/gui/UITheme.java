package com.branchteller.gui;

import com.branchteller.i18n.Messages;

import javax.swing.*;
import javax.swing.plaf.ColorUIResource;
import javax.swing.plaf.FontUIResource;
import javax.swing.plaf.metal.DefaultMetalTheme;
import javax.swing.plaf.metal.MetalLookAndFeel;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.util.Locale;

/**
 * Bank-branded color palette + reusable styling helpers for the whole Swing UI.
 * Switches the look-and-feel to Metal (instead of the native system L&F) because Metal
 * actually honors UIManager color overrides -- the native Windows L&F mostly ignores them,
 * which is why plain UIManager.put() calls alone don't make buttons/tabs colorful there.
 */
public final class UITheme {

    private UITheme() {}

    // ---- Brand palette: deep corporate navy + a single clean blue accent,
    // ---- in the vein of major national banks' digital banking UIs. ----
    public static final Color NAVY         = new Color(0x0A, 0x25, 0x40);
    public static final Color NAVY_DARK    = new Color(0x06, 0x16, 0x27);
    public static final Color NAVY_LIGHT   = new Color(0x1B, 0x3E, 0x63);
    public static final Color ACCENT       = new Color(0x0F, 0x6F, 0xC5);
    public static final Color ACCENT_LIGHT = new Color(0x4D, 0xA3, 0xE0);
    public static final Color TEAL         = new Color(0x1F, 0x7A, 0x6C);
    public static final Color BG_LIGHT     = new Color(0xF3, 0xF5, 0xF8);
    public static final Color PANEL_WHITE  = Color.WHITE;
    public static final Color TEXT_DARK    = new Color(0x1E, 0x27, 0x2E);
    public static final Color DANGER       = new Color(0xB3, 0x26, 0x1E);
    public static final Color SUCCESS      = new Color(0x22, 0x7A, 0x3E);
    public static final Color TAB_IDLE     = new Color(0xDF, 0xE6, 0xEC);
    public static final Color TAB_HOVER    = new Color(0xC7, 0xD6, 0xE3);
    public static final Color TAB_SELECTED = ACCENT;

    private static Font UI_FONT       = new Font("Segoe UI", Font.PLAIN, 12);
    private static Font UI_FONT_BOLD  = new Font("Segoe UI", Font.BOLD, 12);

    /** Picks a font family that actually has glyphs for the current UI language.
     *  Segoe UI covers Latin/Cyrillic/Arabic fine on Windows, but has no Bengali
     *  glyphs -- Bangla text renders as tofu boxes unless we switch to Nirmala UI,
     *  Microsoft's Indic-script UI font (which also has a usable Latin set). */
    private static String uiFontFamily() {
        return "bn".equals(Messages.getLocale().getLanguage()) ? "Nirmala UI" : "Segoe UI";
    }

    /** Public helper so headers/dialogs built outside applyGlobalTheme() can pick up
     *  a script-appropriate font for the currently active locale. */
    public static Font uiFont(int style, int size) {
        return new Font(uiFontFamily(), style, size);
    }

    /** Applies a colorful, bank-branded Metal theme + global component defaults.
     *  Safe to call again after a language switch (e.g. from buildLanguageCombo) to
     *  refresh fonts for the new locale's script before the window is rebuilt. Must
     *  be called once at startup, before any Swing component is constructed. */
    public static void applyGlobalTheme() {
        UI_FONT = new Font(uiFontFamily(), Font.PLAIN, 12);
        UI_FONT_BOLD = new Font(uiFontFamily(), Font.BOLD, 12);

        try {
            MetalLookAndFeel.setCurrentTheme(new BankTheme());
            UIManager.setLookAndFeel(new MetalLookAndFeel());
        } catch (Exception ignored) {
            // fall back silently to whatever L&F is already active
        }

        UIManager.put("Panel.background", BG_LIGHT);
        UIManager.put("OptionPane.background", BG_LIGHT);
        UIManager.put("OptionPane.messageFont", UI_FONT);

        UIManager.put("TabbedPane.background", BG_LIGHT);
        UIManager.put("TabbedPane.selected", ACCENT);
        UIManager.put("TabbedPane.font", UI_FONT_BOLD);
        UIManager.put("TabbedPane.contentAreaColor", PANEL_WHITE);
        UIManager.put("TabbedPane.selectHighlight", ACCENT_LIGHT);

        UIManager.put("Button.background", NAVY);
        UIManager.put("Button.foreground", Color.WHITE);
        UIManager.put("Button.font", UI_FONT_BOLD);
        UIManager.put("Button.select", ACCENT);
        UIManager.put("Button.focus", new ColorUIResource(NAVY_LIGHT));

        UIManager.put("Table.background", PANEL_WHITE);
        UIManager.put("Table.font", UI_FONT);
        UIManager.put("Table.gridColor", new Color(0xE1, 0xE6, 0xEB));
        UIManager.put("Table.selectionBackground", ACCENT_LIGHT);
        UIManager.put("Table.selectionForeground", Color.WHITE);
        UIManager.put("TableHeader.background", NAVY);
        UIManager.put("TableHeader.foreground", Color.WHITE);
        UIManager.put("TableHeader.font", UI_FONT_BOLD);

        UIManager.put("Label.foreground", TEXT_DARK);
        UIManager.put("Label.font", UI_FONT);
        UIManager.put("TitledBorder.titleColor", NAVY_DARK);
        UIManager.put("TitledBorder.font", UI_FONT_BOLD);

        UIManager.put("TextField.selectionBackground", ACCENT_LIGHT);
        UIManager.put("TextField.background", Color.WHITE);
        UIManager.put("TextField.font", UI_FONT);
        UIManager.put("ComboBox.background", Color.WHITE);
        UIManager.put("ComboBox.selectionBackground", ACCENT_LIGHT);
        UIManager.put("ComboBox.selectionForeground", Color.WHITE);
    }

    /** Custom Metal color theme carrying the navy/blue brand palette through the whole L&F. */
    private static class BankTheme extends DefaultMetalTheme {
        @Override public String getName() { return "BranchTellerBank"; }
        @Override protected ColorUIResource getPrimary1() { return new ColorUIResource(NAVY_DARK); }
        @Override protected ColorUIResource getPrimary2() { return new ColorUIResource(NAVY_LIGHT); }
        @Override protected ColorUIResource getPrimary3() { return new ColorUIResource(NAVY_LIGHT); }
        @Override protected ColorUIResource getSecondary1() { return new ColorUIResource(0xB7, 0xC2, 0xCC); }
        @Override protected ColorUIResource getSecondary2() { return new ColorUIResource(0xD8, 0xE0, 0xE8); }
        @Override protected ColorUIResource getSecondary3() { return new ColorUIResource(BG_LIGHT.getRGB()); }
        @Override public FontUIResource getControlTextFont() { return new FontUIResource(UI_FONT); }
        @Override public FontUIResource getSystemTextFont()  { return new FontUIResource(UI_FONT); }
        @Override public FontUIResource getUserTextFont()    { return new FontUIResource(UI_FONT); }
        @Override public FontUIResource getMenuTextFont()    { return new FontUIResource(UI_FONT); }
        @Override public FontUIResource getWindowTitleFont() { return new FontUIResource(UI_FONT_BOLD); }
        @Override public FontUIResource getSubTextFont()     { return new FontUIResource(uiFontFamily(), Font.PLAIN, 11); }
        @Override public ColorUIResource getPrimaryControlHighlight() { return new ColorUIResource(ACCENT_LIGHT); }
    }

    /** Clean, professional bank header banner used at the top of frames -- deep navy bar,
     *  bank name, and a short screen subtitle. No novelty ornamentation, matching the
     *  understated look of major national banks' digital banking chrome. */
    public static JPanel buildHeaderBanner(String title, String subtitle) {
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(NAVY);
        header.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 3, 0, ACCENT),
                BorderFactory.createEmptyBorder(14, 20, 14, 20)));

        JLabel titleLabel = new JLabel(title);
        titleLabel.setForeground(Color.WHITE);
        titleLabel.setFont(uiFont(Font.BOLD, 19));

        JLabel subLabel = new JLabel(subtitle == null ? "" : subtitle);
        subLabel.setForeground(new Color(0xB9, 0xCD, 0xE0));
        subLabel.setFont(uiFont(Font.PLAIN, 12));

        JPanel textStack = new JPanel();
        textStack.setOpaque(false);
        textStack.setLayout(new BoxLayout(textStack, BoxLayout.Y_AXIS));
        titleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        subLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        textStack.add(titleLabel);
        if (subtitle != null && !subtitle.isEmpty()) textStack.add(subLabel);

        header.add(textStack, BorderLayout.WEST);
        return header;
    }

    /**
     * A small language picker meant for the EAST side of a header banner. Selecting a
     * language switches Messages' current locale and invokes onChange, which the caller
     * uses to rebuild the current window (Swing doesn't hot-swap already-built text).
     */
    public static JComboBox<String> buildLanguageCombo(Runnable onChange) {
        JComboBox<String> combo = new JComboBox<>(Messages.displayNames());
        // Segoe UI covers English/Spanish/French/Arabic glyphs fine, but has no Bengali
        // glyphs -- render that one entry ("বাংলা") with Nirmala UI instead so it doesn't
        // show up as tofu boxes while every other entry keeps the normal UI font.
        final Font defaultFont = new Font("Segoe UI", Font.PLAIN, 12);
        final Font bengaliFont = new Font("Nirmala UI", Font.PLAIN, 12);
        combo.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                            boolean isSelected, boolean cellHasFocus) {
                Component c = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                c.setFont("বাংলা".equals(value) ? bengaliFont : defaultFont);
                return c;
            }
        });
        // Swing's BasicComboBoxUI.paintCurrentValue() always repaints the CLOSED box using
        // comboBox.getFont() itself (it overrides whatever font the renderer picked), so the
        // per-item renderer font above only affects the open dropdown list. To keep the closed
        // box readable too, keep the combo's own font in sync with whichever entry is selected.
        combo.setSelectedItem(Messages.displayNameForLocale(Messages.getLocale()));
        combo.setFont("বাংলা".equals(combo.getSelectedItem()) ? bengaliFont : defaultFont);
        combo.setFocusable(false);
        combo.setMaximumRowCount(5);
        combo.addActionListener(e -> {
            String selected = (String) combo.getSelectedItem();
            combo.setFont("বাংলা".equals(selected) ? bengaliFont : defaultFont);
            Locale chosen = Messages.localeForDisplayName(selected);
            if (!chosen.getLanguage().equals(Messages.getLocale().getLanguage())) {
                Messages.setLocale(chosen);
                applyGlobalTheme();
                onChange.run();
            }
        });
        return combo;
    }

    // ---- Button factories ----

    public static JButton primaryButton(String text) {
        JButton b = new JButton(text);
        styleButton(b, NAVY, NAVY_LIGHT, Color.WHITE);
        return b;
    }

    public static JButton accentButton(String text) {
        JButton b = new JButton(text);
        styleButton(b, ACCENT, ACCENT_LIGHT, Color.WHITE);
        return b;
    }

    public static void styleButton(JButton btn, Color base, Color hover, Color fg) {
        btn.setBackground(base);
        btn.setForeground(fg);
        btn.setFocusPainted(false);
        btn.setOpaque(true);
        btn.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(base.darker(), 1),
                BorderFactory.createEmptyBorder(6, 14, 6, 14)));
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.putClientProperty("uiThemeStyled", Boolean.TRUE);
        btn.addMouseListener(new MouseAdapter() {
            @Override public void mouseEntered(MouseEvent e) { btn.setBackground(hover); }
            @Override public void mouseExited(MouseEvent e)  { btn.setBackground(base); }
        });
    }

    /**
     * Walks a component tree and gives every plain JButton found (i.e. one not already
     * styled via primaryButton/accentButton/styleButton) a colorful hover effect + hand
     * cursor. Lets every existing panel pick up hover behavior without editing each one.
     */
    public static void applyHoverRecursively(Component root) {
        if (root instanceof JButton btn) {
            if (btn.getClientProperty("uiThemeStyled") == null) {
                Color base = btn.getBackground();
                Color hover = blend(base, ACCENT_LIGHT);
                btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                btn.setFocusPainted(false);
                btn.putClientProperty("uiThemeStyled", Boolean.TRUE);
                btn.addMouseListener(new MouseAdapter() {
                    @Override public void mouseEntered(MouseEvent e) { if (btn.isEnabled()) btn.setBackground(hover); }
                    @Override public void mouseExited(MouseEvent e)  { btn.setBackground(base); }
                });
            }
        }
        if (root instanceof Container container) {
            for (Component child : container.getComponents()) {
                applyHoverRecursively(child);
            }
        }
    }

    private static Color blend(Color a, Color b) {
        int r = (a.getRed() + b.getRed()) / 2;
        int g = (a.getGreen() + b.getGreen()) / 2;
        int bl = (a.getBlue() + b.getBlue()) / 2;
        return new Color(r, g, bl);
    }

    /** Colorful hover + selected-tab highlighting for a JTabbedPane. */
    public static void styleTabs(JTabbedPane tabs) {
        Runnable repaintSelection = () -> {
            for (int i = 0; i < tabs.getTabCount(); i++) {
                tabs.setBackgroundAt(i, i == tabs.getSelectedIndex() ? TAB_SELECTED : TAB_IDLE);
            }
        };
        repaintSelection.run();
        tabs.addChangeListener(e -> repaintSelection.run());

        tabs.addMouseMotionListener(new MouseMotionAdapter() {
            int lastHover = -1;
            @Override public void mouseMoved(MouseEvent e) {
                int idx = tabs.indexAtLocation(e.getX(), e.getY());
                if (idx == lastHover) return;
                if (lastHover >= 0 && lastHover != tabs.getSelectedIndex()) {
                    tabs.setBackgroundAt(lastHover, TAB_IDLE);
                }
                if (idx >= 0 && idx != tabs.getSelectedIndex()) {
                    tabs.setBackgroundAt(idx, TAB_HOVER);
                }
                lastHover = idx;
            }
        });
        tabs.addMouseListener(new MouseAdapter() {
            @Override public void mouseExited(MouseEvent e) { repaintSelection.run(); }
        });
    }

    // ---- Status badges ----

    private static final Color STATUS_GOOD_BG   = new Color(0xDD, 0xF3, 0xE2);
    private static final Color STATUS_GOOD_FG   = new Color(0x1C, 0x6B, 0x38);
    private static final Color STATUS_WARN_BG   = new Color(0xFC, 0xEF, 0xD3);
    private static final Color STATUS_WARN_FG   = new Color(0x8A, 0x5A, 0x00);
    private static final Color STATUS_BAD_BG    = new Color(0xFB, 0xE0, 0xDE);
    private static final Color STATUS_BAD_FG    = new Color(0xA3, 0x24, 0x1D);
    private static final Color STATUS_NEUTRAL_BG = new Color(0xE4, 0xE9, 0xEE);
    private static final Color STATUS_NEUTRAL_FG = new Color(0x45, 0x51, 0x5C);

    private static final java.util.Set<String> STATUS_GOOD = java.util.Set.of(
            "VERIFIED", "ACTIVE", "APPROVED", "DISBURSED", "PAID", "CLEARED", "YES", "COMPLETED", "ON_TIME");
    private static final java.util.Set<String> STATUS_WARN = java.util.Set.of(
            "PENDING", "APPLIED", "DORMANT", "PARTIAL", "PROCESSING", "IN_PROGRESS");
    private static final java.util.Set<String> STATUS_BAD = java.util.Set.of(
            "REJECTED", "CLOSED", "FAILED", "BOUNCED", "NO", "OVERDUE", "DECLINED");

    /** Installs a colored pill-style renderer on the given table column for status-like text values. */
    public static void installStatusRenderer(JTable table, int columnIndex) {
        table.getColumnModel().getColumn(columnIndex).setCellRenderer(new StatusBadgeRenderer());
    }

    private static class StatusBadgeRenderer extends javax.swing.table.DefaultTableCellRenderer {
        StatusBadgeRenderer() {
            setHorizontalAlignment(SwingConstants.CENTER);
            setOpaque(true);
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
                                                         boolean hasFocus, int row, int column) {
            JLabel label = (JLabel) super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            String text = value == null ? "" : value.toString();
            String key = text.toUpperCase(java.util.Locale.ROOT);

            Color bg, fg;
            if (STATUS_GOOD.contains(key)) { bg = STATUS_GOOD_BG; fg = STATUS_GOOD_FG; }
            else if (STATUS_WARN.contains(key)) { bg = STATUS_WARN_BG; fg = STATUS_WARN_FG; }
            else if (STATUS_BAD.contains(key)) { bg = STATUS_BAD_BG; fg = STATUS_BAD_FG; }
            else { bg = STATUS_NEUTRAL_BG; fg = STATUS_NEUTRAL_FG; }

            if (isSelected) {
                label.setBackground(ACCENT_LIGHT);
                label.setForeground(Color.WHITE);
            } else {
                label.setBackground(bg);
                label.setForeground(fg);
            }
            label.setText(text);
            label.setFont(UI_FONT_BOLD);
            label.setBorder(BorderFactory.createEmptyBorder(2, 8, 2, 8));
            return label;
        }
    }
}
