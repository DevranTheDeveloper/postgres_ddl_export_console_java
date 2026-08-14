package com.ddlexporter.ui;

import javax.swing.*;
import javax.swing.text.*;
import java.awt.*;

public class LogPanel extends JPanel {
    private final JTextPane textPane;
    private final StyledDocument doc;
    private final JLabel statusLabel;
    private final JProgressBar progressBar;
    private final JLabel percentLabel;
    private boolean isDark = false;

    public LogPanel() {
        setLayout(new BorderLayout(5, 5));
        setBorder(BorderFactory.createTitledBorder("Canlı Log Akışı & Durum"));

        textPane = new JTextPane();
        textPane.setEditable(false);
        textPane.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        textPane.setMargin(new Insets(6, 8, 6, 8));
        doc = textPane.getStyledDocument();

        JScrollPane scrollPane = new JScrollPane(textPane);
        scrollPane.setPreferredSize(new Dimension(0, 150));
        add(scrollPane, BorderLayout.CENTER);

        // Bottom Status & Progress Container (Clean layout, natural gaps, no text collision)
        JPanel bottomPanel = new JPanel(new BorderLayout(8, 0));
        bottomPanel.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));

        // 1. Left: Dynamic Status Text (no artificial 280px gap)
        statusLabel = new JLabel("Durum: Hazır");
        statusLabel.setFont(statusLabel.getFont().deriveFont(Font.BOLD, 12f));
        statusLabel.setBorder(BorderFactory.createEmptyBorder(0, 2, 0, 4));
        bottomPanel.add(statusLabel, BorderLayout.WEST);

        // 2. Center: Sleek modern Progress Track + Percentage Label on the right
        JPanel centerProgressPanel = new JPanel(new BorderLayout(8, 0));
        centerProgressPanel.setOpaque(false);

        progressBar = new JProgressBar(0, 100);
        progressBar.setPreferredSize(new Dimension(0, 14));
        progressBar.setStringPainted(false); // Clean track without ugly text overlay
        centerProgressPanel.add(progressBar, BorderLayout.CENTER);

        percentLabel = new JLabel("%0");
        percentLabel.setFont(percentLabel.getFont().deriveFont(Font.BOLD, 11f));
        percentLabel.setPreferredSize(new Dimension(45, 24));
        centerProgressPanel.add(percentLabel, BorderLayout.EAST);

        bottomPanel.add(centerProgressPanel, BorderLayout.CENTER);

        // 3. Right: Clear button
        JButton clearBtn = new JButton("Temizle");
        clearBtn.setPreferredSize(new Dimension(75, 24));
        clearBtn.setFont(clearBtn.getFont().deriveFont(Font.PLAIN, 11f));
        clearBtn.addActionListener(e -> clearLogs());
        bottomPanel.add(clearBtn, BorderLayout.EAST);

        add(bottomPanel, BorderLayout.SOUTH);
        applyTheme(false);
    }

    public void clearLogs() {
        textPane.setText("");
        statusLabel.setText("Durum: Hazır");
        progressBar.setIndeterminate(false);
        progressBar.setValue(0);
        percentLabel.setText("%0");
    }

    public void appendLog(String message) {
        if (message == null)
            return;
        SwingUtilities.invokeLater(() -> {
            try {
                Color timeColor = isDark ? new Color(156, 163, 175) : new Color(107, 114, 128);
                Color infoColor = isDark ? new Color(74, 222, 128) : new Color(22, 163, 74); // Green
                Color errorColor = isDark ? new Color(248, 113, 113) : new Color(220, 38, 38); // Red
                Color warnColor = isDark ? new Color(251, 191, 36) : new Color(217, 119, 6); // Amber
                Color textColor = isDark ? new Color(243, 244, 246) : new Color(31, 41, 55);

                boolean isError = message.contains("[ERROR]") || message.contains("[HATA]")
                        || message.contains("Başarısız") || message.contains("Hata");
                boolean isWarn = message.contains("[WARN]") || message.contains("[UYARI]");
                boolean isSuccess = message.contains("Başarılı") || message.contains("Tamamlandı")
                        || message.contains("[INFO]");

                // Timestamp matching: [YYYY-MM-DD HH:mm:ss]
                if (message.startsWith("[") && message.indexOf("]") > 0 && message.length() > 21) {
                    int firstClose = message.indexOf("]");
                    String timestamp = message.substring(0, firstClose + 1);
                    String rest = message.substring(firstClose + 1).trim();

                    appendStyledText(timestamp + " ", timeColor, false);

                    if (rest.startsWith("[")) {
                        int tagClose = rest.indexOf("]");
                        if (tagClose > 0) {
                            String tag = rest.substring(0, tagClose + 1);
                            String body = rest.substring(tagClose + 1);

                            Color tagColor = isError ? errorColor
                                    : (isWarn ? warnColor : (isSuccess ? infoColor : textColor));
                            appendStyledText(tag, tagColor, true);
                            appendStyledText(body + "\n", textColor, false);
                            scrollToEnd();
                            return;
                        }
                    }

                    appendStyledText(rest + "\n", isError ? errorColor : textColor, false);
                } else {
                    Color msgColor = isError ? errorColor : (isWarn ? warnColor : (isSuccess ? infoColor : textColor));
                    appendStyledText(message + "\n", msgColor, isError);
                }
                scrollToEnd();
            } catch (Exception ignored) {
            }
        });
    }

    private void appendStyledText(String text, Color color, boolean bold) {
        try {
            SimpleAttributeSet set = new SimpleAttributeSet();
            StyleConstants.setForeground(set, color);
            StyleConstants.setBold(set, bold);
            doc.insertString(doc.getLength(), text, set);
        } catch (BadLocationException ignored) {
        }
    }

    private void scrollToEnd() {
        textPane.setCaretPosition(doc.getLength());
    }

    public void setProgressIndeterminate(boolean indeterminate, String statusText) {
        statusLabel.setText("Durum: " + statusText);
        progressBar.setIndeterminate(indeterminate);
        percentLabel.setText(indeterminate ? "..." : "%0");
    }

    public void setProgress(int value, String statusText) {
        statusLabel.setText("Durum: " + statusText);
        progressBar.setIndeterminate(false);
        progressBar.setValue(value);
        percentLabel.setText("%" + value);
    }

    public void applyTheme(boolean isDark) {
        this.isDark = isDark;
        if (isDark) {
            textPane.setBackground(new Color(18, 20, 24));
            textPane.setCaretColor(Color.WHITE);
            statusLabel.setForeground(new Color(220, 225, 235));
            percentLabel.setForeground(new Color(200, 205, 215));
        } else {
            textPane.setBackground(new Color(248, 250, 252));
            textPane.setCaretColor(Color.BLACK);
            statusLabel.setForeground(new Color(30, 40, 55));
            percentLabel.setForeground(new Color(60, 70, 85));
        }
    }
}
