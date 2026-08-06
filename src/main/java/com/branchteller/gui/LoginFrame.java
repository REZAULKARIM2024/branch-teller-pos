package com.branchteller.gui;

import com.branchteller.i18n.Messages;
import com.branchteller.model.User;
import com.branchteller.service.AuthService;

import javax.swing.*;
import java.awt.*;
import java.sql.SQLException;
import java.util.Optional;

public class LoginFrame extends JFrame {

    private final AuthService authService = new AuthService();
    private final JTextField usernameField = new JTextField(18);
    private final JPasswordField passwordField = new JPasswordField(18);

    public LoginFrame() {
        super(Messages.tr("login.windowTitle"));
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setResizable(false);
        setLayout(new BorderLayout());

        JPanel header = UITheme.buildHeaderBanner(Messages.tr("app.bankName"), Messages.tr("login.subtitle"));
        header.add(UITheme.buildLanguageCombo(this::rebuild), BorderLayout.EAST);
        add(header, BorderLayout.NORTH);

        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBackground(UITheme.PANEL_WHITE);
        panel.setBorder(BorderFactory.createEmptyBorder(28, 32, 28, 32));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(8, 6, 8, 6);
        gbc.anchor = GridBagConstraints.WEST;

        JLabel title = new JLabel(Messages.tr("login.signIn"));
        title.setFont(title.getFont().deriveFont(Font.BOLD, 15f));
        title.setForeground(UITheme.NAVY);
        gbc.gridx = 0; gbc.gridy = 0; gbc.gridwidth = 2;
        panel.add(title, gbc);

        gbc.gridwidth = 1;
        gbc.gridy = 1; gbc.gridx = 0; panel.add(new JLabel(Messages.tr("login.username")), gbc);
        gbc.gridx = 1; panel.add(usernameField, gbc);

        gbc.gridy = 2; gbc.gridx = 0; panel.add(new JLabel(Messages.tr("login.password")), gbc);
        gbc.gridx = 1; panel.add(passwordField, gbc);

        JButton loginBtn = UITheme.accentButton(Messages.tr("login.button"));
        gbc.gridy = 3; gbc.gridx = 1; gbc.anchor = GridBagConstraints.EAST;
        panel.add(loginBtn, gbc);

        loginBtn.addActionListener(e -> attemptLogin());
        passwordField.addActionListener(e -> attemptLogin());

        add(panel, BorderLayout.CENTER);
        getContentPane().setBackground(UITheme.PANEL_WHITE);

        applyComponentOrientation(Messages.isRtl() ? ComponentOrientation.RIGHT_TO_LEFT : ComponentOrientation.LEFT_TO_RIGHT);

        pack();
        setLocationRelativeTo(null);

        UITheme.applyHoverRecursively(panel);
    }

    private void rebuild() {
        dispose();
        new LoginFrame().setVisible(true);
    }

    private void attemptLogin() {
        String username = usernameField.getText().trim();
        String password = new String(passwordField.getPassword());

        if (username.isEmpty() || password.isEmpty()) {
            JOptionPane.showMessageDialog(this, Messages.tr("login.missingMsg"), Messages.tr("login.missingTitle"), JOptionPane.WARNING_MESSAGE);
            return;
        }

        try {
            Optional<User> user = authService.verifyPassword(username, password);
            if (user.isEmpty()) {
                JOptionPane.showMessageDialog(this, Messages.tr("login.failedMsg"), Messages.tr("login.failedTitle"), JOptionPane.ERROR_MESSAGE);
                return;
            }

            User u = user.get();
            if (u.isOtpRequired()) {
                if (!completeOtpChallenge(u)) return; // user cancelled or failed OTP
            }

            dispose();
            new MainFrame(u).setVisible(true);
        } catch (com.branchteller.service.AuthService.AccountLockedException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Account Locked", JOptionPane.ERROR_MESSAGE);
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(this,
                    Messages.tr("login.dbErrorMsg") + ex.getMessage(),
                    Messages.tr("login.dbErrorTitle"), JOptionPane.ERROR_MESSAGE);
        }
    }

    /** Simulated two-factor step: an OTP is generated and shown (standing in for an SMS/email
     *  gateway this project doesn't have) and the user must type it back in to proceed. */
    private boolean completeOtpChallenge(User user) throws SQLException {
        String otp = authService.issueOtp(user);
        JOptionPane.showMessageDialog(this,
                "A one-time passcode was sent to your registered phone/email (simulated).\nYour code: " + otp,
                "Two-Factor Authentication", JOptionPane.INFORMATION_MESSAGE);

        for (int attempt = 0; attempt < 3; attempt++) {
            String entered = JOptionPane.showInputDialog(this, "Enter the 6-digit code:", "Two-Factor Authentication", JOptionPane.PLAIN_MESSAGE);
            if (entered == null) return false; // cancelled
            if (authService.verifyOtp(user, entered)) return true;
            JOptionPane.showMessageDialog(this, "Incorrect or expired code. Try again.", "Verification Failed", JOptionPane.WARNING_MESSAGE);
        }
        return false;
    }
}
