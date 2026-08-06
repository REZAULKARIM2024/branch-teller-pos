package com.branchteller.util;

import java.awt.*;
import java.awt.print.PageFormat;
import java.awt.print.Printable;
import java.awt.print.PrinterException;
import java.util.List;

/**
 * Minimal java.awt.print.Printable that lays out a list of plain-text lines top to bottom,
 * paginating automatically. Used for both receipts and statements -- same approach as the
 * POS project's print dialogs for paystubs/paychecks, just generalized to any line list.
 */
public class PrintableText implements Printable {

    private final List<String> lines;
    private final int lineHeight = 16;

    public PrintableText(List<String> lines) {
        this.lines = lines;
    }

    @Override
    public int print(Graphics g, PageFormat pf, int pageIndex) throws PrinterException {
        int linesPerPage = (int) (pf.getImageableHeight() / lineHeight);
        int start = pageIndex * linesPerPage;
        if (start >= lines.size()) return NO_SUCH_PAGE;

        Graphics2D g2 = (Graphics2D) g;
        g2.translate(pf.getImageableX(), pf.getImageableY());
        g2.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 11));

        int y = lineHeight;
        int end = Math.min(start + linesPerPage, lines.size());
        for (int i = start; i < end; i++) {
            g2.drawString(lines.get(i), 0, y);
            y += lineHeight;
        }
        return PAGE_EXISTS;
    }

    /** Opens the native OS print dialog and prints if the user confirms. */
    public static void printLines(List<String> lines) {
        java.awt.print.PrinterJob job = java.awt.print.PrinterJob.getPrinterJob();
        job.setPrintable(new PrintableText(lines));
        if (job.printDialog()) {
            try {
                job.print();
            } catch (PrinterException e) {
                javax.swing.JOptionPane.showMessageDialog(null,
                        "Print failed: " + e.getMessage(), "Print error", javax.swing.JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    /** Lets the user pick a destination .txt file (via a save dialog) and writes the lines to it. */
    public static void saveLines(List<String> lines, java.awt.Component parent) {
        javax.swing.JFileChooser chooser = new javax.swing.JFileChooser();
        chooser.setDialogTitle("Save Letter");
        chooser.setSelectedFile(new java.io.File("letter.txt"));
        int result = chooser.showSaveDialog(parent);
        if (result != javax.swing.JFileChooser.APPROVE_OPTION) return;

        java.io.File file = chooser.getSelectedFile();
        try (java.io.PrintWriter pw = new java.io.PrintWriter(new java.io.FileWriter(file))) {
            for (String line : lines) pw.println(line);
            javax.swing.JOptionPane.showMessageDialog(parent,
                    "Saved to " + file.getAbsolutePath(), "Saved", javax.swing.JOptionPane.INFORMATION_MESSAGE);
        } catch (java.io.IOException e) {
            javax.swing.JOptionPane.showMessageDialog(parent,
                    "Save failed: " + e.getMessage(), "Save error", javax.swing.JOptionPane.ERROR_MESSAGE);
        }
    }
}
