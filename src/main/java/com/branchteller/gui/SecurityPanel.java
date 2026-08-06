package com.branchteller.gui;

import com.branchteller.i18n.Messages;
import com.branchteller.model.User;
import com.branchteller.service.AuthService;

import javax.swing.*;
import java.awt.*;
import java.sql.SQLException;

/** Self-service password change enforcing the bank's password complexity policy
 *  (8+ chars, upper, lower, digit, symbol). Every user sees this tab, at any role. */
public class SecurityPanel extends JPanel {

    private final AuthService authService = new AuthService();
    private final User currentUser;

    private final JPasswordField currentPasswordField = new JPasswordField(18);
    private final JPasswordField newPasswordField = new JPasswordField(18);
    private final JPasswordField confirmPasswordField = new JPasswordField(18);

    public SecurityPanel(User currentUser) {
        this.currentUser = currentUser;
        setLayout(new BorderLayout(10, 10));
        setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

        JPanel form = new JPanel(new GridBagLayout());
        form.setBorder(BorderFactory.createTitledBorder(Messages.tr("security.formTitle")));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(8, 8, 8, 8);
        gbc.anchor = GridBagConstraints.WEST;

        gbc.gridx = 0; gbc.gridy = 0; form.add(new JLabel(Messages.tr("security.currentPassword")), gbc);
        gbc.gridx = 1; form.add(currentPasswordField, gbc);

        gbc.gridx = 0; gbc.gridy = 1; form.add(new JLabel(Messages.tr("security.newPassword")), gbc);
        gbc.gridx = 1; form.add(newPasswordField, gbc);

        gbc.gridx = 0; gbc.gridy = 2; form.add(new JLabel(Messages.tr("security.confirmPassword")), gbc);
        gbc.gridx = 1; form.add(confirmPasswordField, gbc);

        JLabel policyLabel = new JLabel(Messages.tr("security.policyText"));
        policyLabel.setForeground(Color.GRAY);
        gbc.gridx = 0; gbc.gridy = 3; gbc.gridwidth = 2; form.add(policyLabel, gbc);

        JButton changeBtn = new JButton(Messages.tr("security.changeBtn"));
        changeBtn.addActionListener(e -> changePassword());
        gbc.gridx = 1; gbc.gridy = 4; gbc.gridwidth = 1; gbc.anchor = GridBagConstraints.EAST;
        form.add(changeBtn, gbc);

        add(form, BorderLayout.NORTH);

        String otpStatus = Messages.tr(currentUser.isOtpRequired() ? "security.otpEnabled" : "security.otpDisabled");
        JLabel info = new JLabel("<html><br>" + Messages.tr("security.signedInAs") + currentUser.getFullName() + " (" + currentUser.getRole() + ")" +
                "<br>" + Messages.tr("security.otpStatusLine", otpStatus) +
                "<br>" + Messages.tr("security.approvalLimitLine", currentUser.getApprovalLimit()) + "</html>");
        add(info, BorderLayout.CENTER);
    }

    private void changePassword() {
        String current = new String(currentPasswordField.getPassword());
        String newPw = new String(newPasswordField.getPassword());
        String confirm = new String(confirmPasswordField.getPassword());

        if (current.isEmpty() || newPw.isEmpty()) {
            JOptionPane.showMessageDialog(this, Messages.tr("security.missingFieldsMsg"), Messages.tr("common.missingInfoTitle"), JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (!newPw.equals(confirm)) {
            JOptionPane.showMessageDialog(this, Messages.tr("security.mismatchMsg"), Messages.tr("security.mismatchTitle"), JOptionPane.WARNING_MESSAGE);
            return;
        }

        try {
            authService.changePassword(currentUser, current, newPw);
            JOptionPane.showMessageDialog(this, Messages.tr("security.changedMsg"));
            currentPasswordField.setText("");
            newPasswordField.setText("");
            confirmPasswordField.setText("");
        } catch (AuthService.WrongPasswordException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), Messages.tr("security.incorrectPasswordTitle"), JOptionPane.ERROR_MESSAGE);
        } catch (AuthService.WeakPasswordException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), Messages.tr("security.weakPasswordTitle"), JOptionPane.WARNING_MESSAGE);
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(this, Messages.tr("common.databaseErrorPrefix") + ex.getMessage(), Messages.tr("common.error"), JOptionPane.ERROR_MESSAGE);
        }
    }
}
