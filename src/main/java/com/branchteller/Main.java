package com.branchteller;

import com.branchteller.gui.LoginFrame;
import com.branchteller.gui.UITheme;

import javax.swing.*;

public class Main {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            UITheme.applyGlobalTheme();
            new LoginFrame().setVisible(true);
        });
    }
}
