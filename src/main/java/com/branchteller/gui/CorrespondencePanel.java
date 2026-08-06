package com.branchteller.gui;

import com.branchteller.i18n.Messages;
import com.branchteller.model.User;
import com.branchteller.service.CorrespondenceService;
import com.branchteller.service.CorrespondenceService.LetterType;
import com.branchteller.util.PrintableText;

import javax.swing.*;
import java.awt.*;
import java.sql.SQLException;
import java.util.List;

/** Generates and previews official bank correspondence -- letters and certificates -- for print or save. */
public class CorrespondencePanel extends JPanel {

    private final CorrespondenceService correspondenceService = new CorrespondenceService();
    private final User currentUser;

    private final JComboBox<LetterType> letterTypeCombo = new JComboBox<>(LetterType.values());
    private final JTextField accountField = new JTextField(14);
    private final JTextField loanIdField = new JTextField(8);
    private final JTextField extraField = new JTextField(24);
    private final JLabel extraLabel = new JLabel(Messages.tr("corr.extraDefault"));
    private final JTextArea previewArea = new JTextArea();

    private List<String> currentLetterLines;

    public CorrespondencePanel(User currentUser) {
        this.currentUser = currentUser;
        setLayout(new BorderLayout(10, 10));
        setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

        add(buildForm(), BorderLayout.NORTH);
        add(buildPreview(), BorderLayout.CENTER);

        letterTypeCombo.addActionListener(e -> updateFieldState());
        updateFieldState();
    }

    private JPanel buildForm() {
        JPanel form = new JPanel(new GridBagLayout());
        form.setBorder(BorderFactory.createTitledBorder(Messages.tr("corr.title")));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 6, 5, 6);
        gbc.anchor = GridBagConstraints.WEST;

        gbc.gridx = 0; gbc.gridy = 0; form.add(new JLabel(Messages.tr("corr.letterType")), gbc);
        gbc.gridx = 1; gbc.gridwidth = 3; form.add(letterTypeCombo, gbc);
        gbc.gridwidth = 1;

        gbc.gridx = 0; gbc.gridy = 1; form.add(new JLabel(Messages.tr("corr.account")), gbc);
        gbc.gridx = 1; form.add(accountField, gbc);
        gbc.gridx = 2; form.add(new JLabel(Messages.tr("corr.loanId")), gbc);
        gbc.gridx = 3; form.add(loanIdField, gbc);

        gbc.gridx = 0; gbc.gridy = 2; form.add(extraLabel, gbc);
        gbc.gridx = 1; gbc.gridwidth = 3; form.add(extraField, gbc);
        gbc.gridwidth = 1;

        JPanel buttonRow = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton generateBtn = UITheme.primaryButton(Messages.tr("corr.generate"));
        JButton printBtn = UITheme.accentButton(Messages.tr("common.print"));
        JButton saveBtn = new JButton(Messages.tr("common.saveToFile"));
        generateBtn.addActionListener(e -> generateLetter());
        printBtn.addActionListener(e -> printLetter());
        saveBtn.addActionListener(e -> saveLetter());
        buttonRow.add(generateBtn);
        buttonRow.add(printBtn);
        buttonRow.add(saveBtn);
        gbc.gridx = 0; gbc.gridy = 3; gbc.gridwidth = 4; gbc.anchor = GridBagConstraints.EAST;
        form.add(buttonRow, gbc);

        return form;
    }

    private JPanel buildPreview() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createTitledBorder(Messages.tr("corr.previewTitle")));
        previewArea.setEditable(false);
        previewArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        previewArea.setBackground(Color.WHITE);
        panel.add(new JScrollPane(previewArea), BorderLayout.CENTER);
        return panel;
    }

    private void updateFieldState() {
        LetterType type = (LetterType) letterTypeCombo.getSelectedItem();
        boolean needsLoan = type == LetterType.LOAN_SANCTION;
        loanIdField.setEnabled(needsLoan);
        accountField.setEnabled(!needsLoan);

        boolean needsExtra;
        switch (type) {
            case NO_OBJECTION_CERTIFICATE:
                extraLabel.setText(Messages.tr("corr.extraNoc"));
                needsExtra = true;
                break;
            case REFERENCE_LETTER:
                extraLabel.setText(Messages.tr("corr.extraRef"));
                needsExtra = true;
                break;
            case INTEREST_CERTIFICATE:
                extraLabel.setText(Messages.tr("corr.extraInterest"));
                needsExtra = true;
                break;
            default:
                extraLabel.setText(Messages.tr("corr.extraDefault"));
                needsExtra = false;
                break;
        }
        extraField.setEnabled(needsExtra);
    }

    private void generateLetter() {
        LetterType type = (LetterType) letterTypeCombo.getSelectedItem();
        try {
            currentLetterLines = correspondenceService.generate(
                    type, accountField.getText(), loanIdField.getText(), extraField.getText());
            previewArea.setText(String.join("\n", currentLetterLines));
            previewArea.setCaretPosition(0);
        } catch (IllegalArgumentException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), Messages.tr("corr.cannotGenerateTitle"), JOptionPane.WARNING_MESSAGE);
        } catch (SQLException ex) {
            showDbError(ex);
        }
    }

    private void printLetter() {
        if (currentLetterLines == null) {
            JOptionPane.showMessageDialog(this, Messages.tr("corr.nothingToPrintMsg"), Messages.tr("teller.nothingToPrintTitle"), JOptionPane.WARNING_MESSAGE);
            return;
        }
        PrintableText.printLines(currentLetterLines);
    }

    private void saveLetter() {
        if (currentLetterLines == null) {
            JOptionPane.showMessageDialog(this, Messages.tr("corr.nothingToSaveMsg"), Messages.tr("corr.nothingToSaveTitle"), JOptionPane.WARNING_MESSAGE);
            return;
        }
        PrintableText.saveLines(currentLetterLines, this);
    }

    private void showDbError(SQLException ex) {
        JOptionPane.showMessageDialog(this, Messages.tr("common.dbErrorPrefix") + ex.getMessage(), Messages.tr("common.error"), JOptionPane.ERROR_MESSAGE);
    }
}
