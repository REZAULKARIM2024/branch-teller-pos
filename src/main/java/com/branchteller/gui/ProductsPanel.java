package com.branchteller.gui;

import com.branchteller.i18n.Messages;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Read-only reference tab showing NY Financial Bank's own product & service lineup by
 * customer segment (Personal / Business / Commercial) so tellers can quickly look up what
 * the bank offers when a customer asks. Informational only -- not tied to live account data,
 * and uses our own branding throughout (no third-party names, logos, or links).
 */
public class ProductsPanel extends JPanel {

    /** Package-private (not private) so {@code ProductsContentTest} can read the real category/
     *  item structure directly as its source of truth, instead of duplicating this list inside
     *  the test and risking the two silently drifting apart -- the same reasoning {@code
     *  AboutContentTest} follows by reading {@code messages.properties}' {@code tab.*} keys
     *  directly rather than hand-maintaining a parallel copy. */
    static final String[] CATEGORY_KEYS = {"personal", "business", "commercial"};
    static final Map<String, String[]> CATEGORY_ITEMS = new LinkedHashMap<>();
    static {
        CATEGORY_ITEMS.put("personal", new String[]{
                "checking", "savings", "creditCards", "homeLoans", "auto", "investing", "education", "travel"});
        CATEGORY_ITEMS.put("business", new String[]{
                "checking", "loans", "acceptCards", "creditCards", "support", "resources"});
        CATEGORY_ITEMS.put("commercial", new String[]{
                "treasury", "lending", "trade", "capitalMarkets"});
    }

    private final JList<String> categoryList;
    private final JPanel productGrid = new JPanel(new GridLayout(0, 2, 12, 12));

    public ProductsPanel() {
        setLayout(new BorderLayout(10, 10));
        setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

        String[] categoryDisplay = new String[CATEGORY_KEYS.length];
        for (int i = 0; i < CATEGORY_KEYS.length; i++) {
            categoryDisplay[i] = Messages.tr("products.category." + CATEGORY_KEYS[i]);
        }
        categoryList = new JList<>(categoryDisplay);
        categoryList.setName("productsCategoryList");
        categoryList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        categoryList.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        categoryList.setFixedCellHeight(32);

        JScrollPane listScroll = new JScrollPane(categoryList);
        listScroll.setBorder(BorderFactory.createTitledBorder(Messages.tr("products.categoriesTitle")));
        listScroll.setPreferredSize(new Dimension(190, 0));

        productGrid.setName("productsGrid");
        productGrid.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
        productGrid.setBackground(UITheme.BG_LIGHT);
        JScrollPane gridScroll = new JScrollPane(productGrid);
        gridScroll.setBorder(BorderFactory.createTitledBorder(Messages.tr("products.title")));
        gridScroll.getVerticalScrollBar().setUnitIncrement(16);

        add(listScroll, BorderLayout.WEST);
        add(gridScroll, BorderLayout.CENTER);

        categoryList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) showCategory(categoryList.getSelectedIndex());
        });
        categoryList.setSelectedIndex(0);
    }

    private void showCategory(int index) {
        if (index < 0) return;
        productGrid.removeAll();
        String categoryKey = CATEGORY_KEYS[index];
        for (String itemKey : CATEGORY_ITEMS.get(categoryKey)) {
            String name = Messages.tr("products." + categoryKey + "." + itemKey);
            String desc = Messages.tr("products." + categoryKey + "." + itemKey + ".desc");
            productGrid.add(buildCard(name, desc));
        }
        productGrid.revalidate();
        productGrid.repaint();
    }

    private JPanel buildCard(String name, String desc) {
        JPanel card = new JPanel(new BorderLayout(4, 4));
        card.setBackground(Color.WHITE);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(0xDF, 0xE6, 0xEC)),
                BorderFactory.createEmptyBorder(10, 12, 10, 12)));

        JLabel nameLabel = new JLabel(name);
        nameLabel.setFont(new Font("Segoe UI", Font.BOLD, 13));
        nameLabel.setForeground(UITheme.NAVY);

        JLabel descLabel = new JLabel("<html><body style='width:220px'>" + desc + "</body></html>");
        descLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        descLabel.setForeground(UITheme.TEXT_DARK);

        card.add(nameLabel, BorderLayout.NORTH);
        card.add(descLabel, BorderLayout.CENTER);

        card.addMouseListener(new MouseAdapter() {
            @Override public void mouseEntered(MouseEvent e) { card.setBackground(new Color(0xEE, 0xF4, 0xFA)); }
            @Override public void mouseExited(MouseEvent e) { card.setBackground(Color.WHITE); }
        });

        return card;
    }
}
