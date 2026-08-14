package com.ddlexporter.ui;

import javax.swing.*;
import javax.swing.text.*;
import java.awt.*;

public class LogPanel extends JPanel {
    private final JTextPane textPane;
    private final StyledDocument doc;
    private final JProgressBar progressBar;
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

        JPanel bottomPanel = new JPanel(new BorderLayout(8, 0));
        bottomPanel.setBorder(BorderFactory.createEmptyBorder(2, 2, 2, 2));

        progressBar = new JProgressBar();
        progressBar.setStringPainted(true);
        progressBar.setString("Hazır");
        bottomPanel.add(progressBar, BorderLayout.CENTER);

        JButton clearBtn = new JButton("Temizle");
        clearBtn.setFont(clearBtn.getFont().deriveFont(Font.PLAIN, 11f));
        clearBtn.addActionListener(e -> textPane.setText(""));
        bottomPanel.add(clearBtn, BorderLayout.EAST);

        add(bottomPanel, BorderLayout.SOUTH);
        applyTheme(false);
    }

    public void appendLog(String message) {
        if (message == null)
            return;
        SwingUtilities.invokeLater(() -> {
            try {
                // Color palette
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
        progressBar.setIndeterminate(indeterminate);
        progressBar.setString(statusText);
    }

    public void setProgress(int value, String statusText) {
        progressBar.setIndeterminate(false);
        progressBar.setValue(value);
        progressBar.setString(statusText);
    }

    public void applyTheme(boolean isDark) {
        this.isDark = isDark;
        if (isDark) {
            textPane.setBackground(new Color(18, 20, 24));
            textPane.setCaretColor(Color.WHITE);
        } else {
            textPane.setBackground(new Color(248, 250, 252));
            textPane.setCaretColor(Color.BLACK);
        }
    }
}
